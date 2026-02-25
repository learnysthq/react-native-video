export interface VideoTrack {
  /**
   * Unique identifier for the video track
   */
  id: string;

  /**
   * Video width in pixels
   */
  width: number;

  /**
   * Video height in pixels
   */
  height: number;

  /**
   * Bitrate in bits per second
   */
  bitrate: number;

  /**
   * Whether this track is currently selected
   */
  selected: boolean;
}
