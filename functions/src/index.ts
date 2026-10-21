import * as admin from "firebase-admin";
import { onValueWritten } from "firebase-functions/v2/database";
import { logger } from "firebase-functions/v2";

admin.initializeApp();

const firestore = admin.firestore();
const messaging = admin.messaging();

// ── Distance thresholds (meters) mapped to alert minutes ──
// These approximate how far a shuttle might be at campus speeds (≈15–20 km/h).
const THRESHOLD_MAP: Record<number, number> = {
  10: 2500, // 10 min → ~2.5 km
  5: 1200,  // 5 min  → ~1.2 km
  2: 500,   // 2 min  → ~500 m
};

interface ShuttleData {
  latitude?: number;
  longitude?: number;
  heading?: number;
  updatedAtMillis?: number;
}

interface SeatData {
  availableSeats?: number;
  totalSeats?: number;
}

interface StopDoc {
  name?: string;
  latitude?: number;
  longitude?: number;
  order?: number;
}

/**
 * Triggers every time a shuttle's location is written to RTDB at
 * `shuttles/{shuttleId}`. For each stop within a threshold distance,
 * sends an FCM push to the matching topic.
 */
export const onShuttleLocationUpdate = onValueWritten(
  {
    ref: "shuttles/{shuttleId}",
    region: "us-central1",
  },
  async (event) => {
    const shuttleId = event.params.shuttleId;
    const data = event.data.after.val() as ShuttleData | null;

    if (!data?.latitude || !data?.longitude) {
      logger.warn(`Shuttle ${shuttleId}: missing lat/lng, skipping.`);
      return;
    }

    const shuttleLat = data.latitude;
    const shuttleLng = data.longitude;

    // 1. Read all stops from Firestore
    const stopsSnapshot = await firestore.collection("stops").get();
    if (stopsSnapshot.empty) {
      logger.info("No stops in Firestore, skipping.");
      return;
    }

    // 2. Read seat availability for this shuttle
    let seatText = "";
    try {
      const seatDoc = await firestore
        .collection("seatAvailability")
        .doc(shuttleId)
        .get();
      if (seatDoc.exists) {
        const seatData = seatDoc.data() as SeatData;
        const available = seatData.availableSeats ?? 0;
        seatText = `${available} seat${available !== 1 ? "s" : ""} left`;
      }
    } catch (err) {
      logger.warn(`Could not read seat availability for ${shuttleId}`, err);
    }

    // 3. For each stop, check distance and send notifications
    const sendPromises: Promise<string>[] = [];

    for (const stopDoc of stopsSnapshot.docs) {
      const stop = stopDoc.data() as StopDoc;
      if (!stop.latitude || !stop.longitude) continue;

      const distanceMeters = haversineDistance(
        shuttleLat,
        shuttleLng,
        stop.latitude,
        stop.longitude
      );

      const stopName = stop.name || stopDoc.id;
      const stopId = stopDoc.id;

      // Check each threshold
      for (const [minutes, thresholdMeters] of Object.entries(THRESHOLD_MAP)) {
        if (distanceMeters <= thresholdMeters) {
          const topic = `stop_${stopId}_${minutes}min`;
          const title = `🚐 Shuttle approaching ${stopName}`;
          const body = seatText
            ? `Shuttle ${shuttleId} is ~${minutes} min away – ${seatText}`
            : `Shuttle ${shuttleId} is ~${minutes} min away`;

          logger.info(
            `Sending to topic "${topic}": ${body} (distance: ${Math.round(distanceMeters)}m)`
          );

          const promise = messaging
            .send({
              topic,
              notification: { title, body },
              data: {
                type: "SHUTTLE_ARRIVAL",
                shuttleId,
                stopId,
                stopName,
                minutesAway: String(minutes),
              },
              android: {
                priority: "high",
                notification: {
                  channelId: "shuttle_alerts",
                  sound: "default",
                },
              },
            })
            .catch((err) => {
              logger.error(`Failed to send to topic "${topic}"`, err);
              return "";
            });

          sendPromises.push(promise);
        }
      }
    }

    const results = await Promise.all(sendPromises);
    const sent = results.filter((r) => r !== "").length;
    if (sent > 0) {
      logger.info(`Sent ${sent} notification(s) for shuttle ${shuttleId}.`);
    }
  }
);

/**
 * Calculates the distance in meters between two lat/lng pairs
 * using the Haversine formula.
 */
function haversineDistance(
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number
): number {
  const R = 6_371_000; // Earth radius in meters
  const toRad = (deg: number) => (deg * Math.PI) / 180;

  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);

  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;

  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

  return R * c;
}
