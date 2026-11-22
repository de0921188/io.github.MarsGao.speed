package io.github.MarsGao.speed;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Small, versioned UTF-8 key=value format; parsing never writes preferences. */
final class SpeedBackup {
    static final int MAX_CHARS = 65536;

    static String encode(SpeedSettings.FloatReader reader, Set<String> packages) {
        StringBuilder text = new StringBuilder("# VideoSpeed backup\nversion=1\nspeed=")
                .append(SpeedSettings.resolve(reader, null, SpeedSettings.DEFAULT_SPEED)).append('\n');
        for (String pkg : packages) {
            float speed = SpeedSettings.override(reader, pkg);
            if (SpeedSettings.isValid(speed)) text.append(SpeedSettings.appKey(pkg)).append('=').append(speed).append('\n');
            float enabled = SpeedSettings.enabledValue(reader, pkg, Float.NaN);
            if (!Float.isNaN(enabled)) text.append(SpeedSettings.enabledKey(pkg)).append('=').append(enabled).append('\n');
            for (String prefix : new String[]{SpeedSettings.IDLE_APP_PREFIX,SpeedSettings.POLICY_PREFIX,SpeedSettings.PLAYING_PREFIX}) {
                float value;
                try { value = reader.get(prefix+pkg,Float.NaN); } catch (ClassCastException bad) { continue; }
                if (prefix.equals(SpeedSettings.IDLE_APP_PREFIX)
                        ? value == -1f || value == 0f || SpeedSettings.validIdleMinutes(value)
                        : SpeedSettings.supportsPolicy(pkg) && (value == 0f || value == 1f || prefix.equals(SpeedSettings.POLICY_PREFIX) && value == 2f))
                    text.append(prefix).append(pkg).append('=').append(value).append('\n');
            }
        }
        // Optional keys retain version-1 compatibility with existing backups lacking idle settings.
        for (String key : new String[]{SpeedSettings.IDLE_ENABLED, SpeedSettings.IDLE_MINUTES}) {
            float value;
            try { value = reader.get(key, Float.NaN); }
            catch (ClassCastException invalidType) { continue; }
            boolean valid = SpeedSettings.IDLE_ENABLED.equals(key) ? value == 0f || value == 1f
                    : SpeedSettings.validIdleMinutes(value);
            if (valid) text.append(key).append('=').append(value).append('\n');
        }
        return text.toString();
    }

    static Map<String, Float> parse(String text, Set<String> packages) {
        if (text == null || text.length() > MAX_CHARS) throw new IllegalArgumentException("Backup too large");
        Map<String, Float> values = new LinkedHashMap<>();
        boolean version = false;
        for (String raw : text.replace("\uFEFF", "").split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int equal = line.indexOf('=');
            if (equal <= 0 || equal != line.lastIndexOf('=')) throw new IllegalArgumentException("Invalid line");
            String key = line.substring(0, equal).trim(), value = line.substring(equal + 1).trim();
            if ("version".equals(key)) {
                if (version || !"1".equals(value)) throw new IllegalArgumentException("Unsupported version");
                version = true;
                continue;
            }
            boolean enabled = key.startsWith(SpeedSettings.ENABLED_PREFIX);
            boolean idleEnabled = SpeedSettings.IDLE_ENABLED.equals(key);
            boolean idleMinutes = SpeedSettings.IDLE_MINUTES.equals(key);
            boolean idleRule = key.startsWith(SpeedSettings.IDLE_APP_PREFIX);
            boolean policy = key.startsWith(SpeedSettings.POLICY_PREFIX);
            boolean playing = key.startsWith(SpeedSettings.PLAYING_PREFIX);
            String prefix = enabled ? SpeedSettings.ENABLED_PREFIX : idleRule ? SpeedSettings.IDLE_APP_PREFIX
                    : policy ? SpeedSettings.POLICY_PREFIX : playing ? SpeedSettings.PLAYING_PREFIX : SpeedSettings.APP_PREFIX;
            if (!idleEnabled && !idleMinutes && !SpeedSettings.GLOBAL_KEY.equals(key)
                    && (!key.startsWith(prefix) || !packages.contains(key.substring(prefix.length())))) {
                throw new IllegalArgumentException("Unsupported key: " + key);
            }
            if ((policy || playing) && !SpeedSettings.supportsPolicy(key.substring(prefix.length())))
                throw new IllegalArgumentException("Unsupported policy package");
            float number;
            try { number = Float.parseFloat(value); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid number: " + key); }
            if (idleRule ? number != -1f && number != 0f && !SpeedSettings.validIdleMinutes(number)
                    : playing ? number != 0f && number != 1f
                    : policy ? number != 0f && number != 1f && number != 2f
                    : enabled || idleEnabled ? number != 0f && number != 1f
                    : idleMinutes ? !SpeedSettings.validIdleMinutes(number) : !SpeedSettings.isValid(number)) {
                throw new IllegalArgumentException("Invalid value: " + key);
            }
            if (values.put(key, number) != null) throw new IllegalArgumentException("Duplicate key: " + key);
        }
        if (!version || !values.containsKey(SpeedSettings.GLOBAL_KEY)) {
            throw new IllegalArgumentException("Missing version or global speed");
        }
        return values;
    }
}
