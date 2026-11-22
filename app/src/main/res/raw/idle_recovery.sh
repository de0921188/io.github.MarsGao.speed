#!/system/bin/sh
# Owned by VideoSpeed. No global boot scripts, settings, or other apps are changed.
dir="$1"
pkg=io.github.MarsGao.speed
enabled="$dir/enabled"
lock="$dir/guard.lock"
beat="$dir/heartbeat"
[ -f "$enabled" ] || exit 0
if ! mkdir "$lock" 2>/dev/null; then
    old=$(cat "$lock/pid" 2>/dev/null)
    case "$old" in ''|*[!0-9]*) old=0;; esac
    if [ "$old" -gt 1 ] && kill -0 "$old" 2>/dev/null; then exit 0; fi
    rm -f "$lock/pid"
    rmdir "$lock" 2>/dev/null || exit 1
    mkdir "$lock" 2>/dev/null || exit 1
fi
echo $$ > "$lock/pid"
trap 'rm -f "$lock/pid"; rmdir "$lock" 2>/dev/null' EXIT
trap 'exit 0' INT TERM
echo "guard started pid=$$"
while [ -f "$enabled" ]; do
    now=$(date +%s)
    last=$(cat "$beat" 2>/dev/null)
    case "$last" in ''|*[!0-9]*) last=0;; esac
    if ! pidof "$pkg" >/dev/null 2>&1 || [ $((now - last)) -ge 20 ]; then
        # Explicit user force-stop must be respected. Do not clear its stopped flag.
        state=$(dumpsys package "$pkg" 2>/dev/null | sed -n '/User 0:/p')
        case "$state" in *"stopped=true"*) echo 'guard stopped: package force-stopped'; break;; esac
        [ -d "$dir" ] && [ -f "$enabled" ] || break
        echo "recover service at=$now"
        am start-foreground-service --user 0 -n "$pkg/.IdleLockService"
    fi
    sleep 5
done
echo 'guard exited'
