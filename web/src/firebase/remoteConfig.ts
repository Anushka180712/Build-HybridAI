import { getRemoteConfig, fetchAndActivate, getValue } from 'firebase/remote-config';
import { log } from '../lib/log';
import { getFirebase } from './app';

/**
 * Tunables served by Firebase Remote Config, with safe in-app defaults so the
 * app behaves identically when Remote Config is not set up.
 */
export interface ChaukasRemoteConfig {
  /** Cloud model used when inference falls back to (or prefers) the cloud. */
  cloudModel: string;
  /** Risk scores inside this band trigger the "second opinion" suggestion. */
  secondOpinionMin: number;
  secondOpinionMax: number;
  /** Short banner about a scam wave happening right now. Empty = hidden. */
  trendBanner: string;
  trendBannerLink: string;
  dojoEnabled: boolean;
}

export const RC_DEFAULTS: ChaukasRemoteConfig = {
  cloudModel: 'gemini-3.5-flash-lite',
  secondOpinionMin: 35,
  secondOpinionMax: 70,
  trendBanner: 'Scam wave: fake electricity-bill and KYC "update" texts are spiking. Never pay or share an OTP through a texted link.',
  trendBannerLink: 'https://sancharsaathi.gov.in/sfc/',
  dojoEnabled: true,
};

let cached: ChaukasRemoteConfig = { ...RC_DEFAULTS };

export function currentRemoteConfig(): ChaukasRemoteConfig {
  return cached;
}

export async function loadRemoteConfig(): Promise<ChaukasRemoteConfig> {
  const fb = getFirebase();
  if (!fb) return cached;
  try {
    const rc = getRemoteConfig(fb.app);
    rc.settings.minimumFetchIntervalMillis = import.meta.env.DEV ? 60_000 : 3_600_000;
    rc.defaultConfig = {
      cloud_model: RC_DEFAULTS.cloudModel,
      second_opinion_min: RC_DEFAULTS.secondOpinionMin,
      second_opinion_max: RC_DEFAULTS.secondOpinionMax,
      trend_banner: RC_DEFAULTS.trendBanner,
      trend_banner_link: RC_DEFAULTS.trendBannerLink,
      dojo_enabled: RC_DEFAULTS.dojoEnabled,
    };
    await fetchAndActivate(rc);
    cached = {
      cloudModel: getValue(rc, 'cloud_model').asString() || RC_DEFAULTS.cloudModel,
      secondOpinionMin: getValue(rc, 'second_opinion_min').asNumber(),
      secondOpinionMax: getValue(rc, 'second_opinion_max').asNumber(),
      trendBanner: getValue(rc, 'trend_banner').asString(),
      trendBannerLink: getValue(rc, 'trend_banner_link').asString(),
      dojoEnabled: getValue(rc, 'dojo_enabled').asBoolean(),
    };
  } catch (err) {
    log.warn('remote-config', 'unavailable, using in-app defaults', err);
  }
  return cached;
}
