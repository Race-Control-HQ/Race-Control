/** Milliseconds the replay spends "on" each lap while playing; shared by the
 * lap-advance interval in ReplayTab and the sub-lap car animation in
 * ReplayTrackMap so the two stay in sync. Real laps take ~80-100s; this is a
 * deliberately-compressed replay speed, not real time. */
export const REPLAY_TICK_MS = 14000;
