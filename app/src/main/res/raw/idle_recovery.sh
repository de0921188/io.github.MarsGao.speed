#!/system/bin/sh
# Owned by VideoSpeed. No global boot scripts, settings, or other apps are changed.
dir="$1"
pkg=io.github.MarsGao.speed
user="${2:-0}"
case "$user" in ''|*[!0-9]*) exit 1;; esac
log() {
    bytes=$(wc -c < "$dir/guard.log" 2>/dev/null)
    if [ "${bytes:-0}" -ge 65536 ]; then mv -f "$dir/guard.log" "$dir/guard.log.1"; fi
    echo "$*" >> "$dir/guard.log"
}
enabled="$dir/enabled"
lock="$dir/guard.lock"
beat="$dir/heartbeat"
[ -f "$enabled" ] || exit 0
if ! mkdir "$lock" 2>/dev/null; then
    old=$(cat "$lock/pid" 2>/dev/null)
    case "$old" in ''|*[!0-9]*) old=0;; esac
    if [ "$old" -gt 1 ] && kill -0 "$old" 2>/dev/null; then exit 0; fi
    rm -f "$lock/pid" "$lock/protocol"
    rmdir "$lock" 2>/dev/null || exit 1
    mkdir "$lock" 2>/dev/null || exit 1
fi
echo $$ > "$lock/pid"
echo 3 > "$lock/protocol"
trap 'rm -f "$lock/pid" "$lock/protocol"; rmdir "$lock" 2>/dev/null' EXIT
trap 'exit 0' INT TERM
log "guard started pid=$$ user=$user"
while [ -f "$enabled" ]; do
    read uptime rest < /proc/uptime
    now=${uptime%%.*}
    beat_pid=0 last=0
    read beat_pid last < "$beat" 2>/dev/null
    case "$beat_pid" in ''|*[!0-9]*) beat_pid=0;; esac
    case "$last" in ''|*[!0-9]*) last=0;; esac
    process_ok=false
    case " $(pidof "$pkg" 2>/dev/null) " in *" $beat_pid "*) process_ok=true;; esac
    if [ "$process_ok" != true ] || [ $((now - last)) -ge 20 ] || [ "$last" -gt "$now" ]; then
        # Explicit user force-stop must be respected. Do not clear its stopped flag.
        state=$(dumpsys package "$pkg" 2>/dev/null | sed -n "/User $user:/p")
        case "$state" in *"stopped=true"*) log 'guard stopped: package force-stopped'; break;; esac
        [ -d "$dir" ] && [ -f "$enabled" ] || break
        log "recover service at=$now"
        count=$(cat "$dir/recoveries" 2>/dev/null)
        case "$count" in ''|*[!0-9]*) count=0;; esac
        echo $((count + 1)) > "$dir/recoveries"
        am start-foreground-service --user "$user" -n "$pkg/.IdleLockService" >> "$dir/guard.log" 2>&1
    fi
    sleep 5
done
log 'guard exited'
