// Colour tokens shared by the UI illustrations.
//
// These mirror the values the real apps use, where "colour is data": official
// constructor liveries and the FIA tyre compound colours. The apps treat these
// as semantic (which is exactly why Android disables Material You dynamic
// colour — see RaceControlAndroid/docs/FEATURES.md §5), so the illustrations
// have to use the same values or they'd misrepresent the UI.

export const TEAM_COLORS: Record<string, string> = {
  redbull: "#3671C6",
  ferrari: "#E8002D",
  mercedes: "#27F4D2",
  mclaren: "#FF8000",
  astonmartin: "#229971",
  alpine: "#FF87BC",
  williams: "#64C4FF",
  rb: "#6692FF",
  sauber: "#52E252",
  haas: "#B6BABD",
};

export const TYRE_COLORS: Record<string, string> = {
  S: "#DA291C",
  M: "#FFD12E",
  H: "#F0F0EC",
  I: "#43B02A",
  W: "#0067AD",
};

export const FLAG_COLORS = {
  YELLOW: "#FFD500",
  DOUBLE_YELLOW: "#FF9500",
  RED: "#FF453A",
  SC: "#FF6A00",
  VSC: "#AF52DE",
} as const;
