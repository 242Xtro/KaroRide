package de.markus.aceride.core;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static de.markus.aceride.core.CameraAdapter.Camera;

/** Immutable, locally persisted profiles. No live status or battery is persisted. */
public final class CameraProfiles {
    public record Profile(Camera camera, boolean pause, String wakeId) {
        public Profile {
            Objects.requireNonNull(camera);
            if (wakeId != null && (!CameraModels.ACE.equals(camera.model()) || !wakeId.matches("[A-Z0-9]{6}")))
                throw new IllegalArgumentException("Ungültige Aufweckkennung im Kameraprofil");
        }
    }
    private final List<Profile> profiles;
    private final String active;
    public CameraProfiles(List<Profile> profiles, String active) {
        if (profiles.size() > 8) throw new IllegalArgumentException("Höchstens acht Kameras speichern");
        Set<String> addresses = new HashSet<>();
        for (Profile p : profiles) if (!addresses.add(p.camera.address()))
            throw new IllegalArgumentException("Kamera doppelt gespeichert");
        if (active != null && !addresses.contains(active)) throw new IllegalArgumentException("Aktive Kamera fehlt");
        this.profiles = List.copyOf(profiles); this.active = active;
    }
    public static CameraProfiles empty() { return new CameraProfiles(List.of(), null); }
    public static CameraProfiles migrate(Camera old, boolean pause, String wakeAddress, String wakeId) {
        if (old == null) return empty();
        String wake = old.address().equals(wakeAddress) && CameraModels.ACE.equals(old.model()) ? wakeId : null;
        if (wake != null && !wake.matches("[A-Z0-9]{6}")) wake = null;
        return new CameraProfiles(List.of(new Profile(old, pause, wake)), old.address());
    }
    public List<Profile> all() { return profiles; }
    public Profile get(String address) { return profiles.stream().filter(p -> p.camera.address().equals(address)).findFirst().orElse(null); }
    public Profile selected() { return active == null ? null : get(active); }
    public CameraProfiles select(Camera camera) {
        ArrayList<Profile> next = new ArrayList<>(profiles);
        Profile old = get(camera.address());
        if (old != null && !old.camera.model().equals(camera.model())) throw new IllegalArgumentException("Modell der gespeicherten Kamera stimmt nicht überein");
        Profile value = new Profile(camera, old != null && old.pause, old == null ? null : old.wakeId);
        if (old == null) next.add(value); else next.set(next.indexOf(old), value);
        return new CameraProfiles(next, camera.address());
    }
    public CameraProfiles pause(String address, boolean value) {
        Profile old = Objects.requireNonNull(get(address), "Kameraprofil fehlt");
        return replace(old, new Profile(old.camera, value, old.wakeId));
    }
    public CameraProfiles wake(String address, String serial) {
        Profile old = Objects.requireNonNull(get(address), "Kameraprofil fehlt");
        return replace(old, new Profile(old.camera, old.pause, SdkStatusMapper.wakeIdentifier(serial)));
    }
    private CameraProfiles replace(Profile old, Profile value) {
        ArrayList<Profile> next = new ArrayList<>(profiles); next.set(next.indexOf(old), value);
        return new CameraProfiles(next, active);
    }
    public CameraProfiles forgetSelected() {
        if (active == null) return this;
        ArrayList<Profile> kept = new ArrayList<>();
        for (Profile p : profiles) if (!p.camera.address().equals(active)) kept.add(p);
        return new CameraProfiles(kept, null);
    }
    private static String enc(String s) { return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    private static String dec(String s) { return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8); }
    public String encode() {
        StringBuilder s = new StringBuilder("1\n").append(active == null ? "" : enc(active));
        for (Profile p : profiles) s.append('\n').append(enc(p.camera.address())).append('|').append(enc(p.camera.bleName()))
            .append('|').append(p.camera.model()).append('|').append(p.pause ? "1" : "0").append('|').append(p.wakeId == null ? "" : p.wakeId);
        return s.toString();
    }
    public static CameraProfiles decode(String raw) {
        if (raw == null || raw.length() > 16384) throw new IllegalArgumentException("Kameraprofile ungültig");
        String[] lines = raw.split("\n", -1);
        if (lines.length < 2 || !lines[0].equals("1")) throw new IllegalArgumentException("Unbekanntes Kameraprofilformat");
        ArrayList<Profile> values = new ArrayList<>();
        for (int i = 2; i < lines.length; i++) {
            String[] p = lines[i].split("\\|", -1);
            if (p.length != 5 || !(p[3].equals("0") || p[3].equals("1"))) throw new IllegalArgumentException("Kameraprofil beschädigt");
            values.add(new Profile(new Camera(dec(p[0]), dec(p[1]), p[2]), p[3].equals("1"), p[4].isEmpty() ? null : p[4]));
        }
        return new CameraProfiles(values, lines[1].isEmpty() ? null : dec(lines[1]));
    }
}
