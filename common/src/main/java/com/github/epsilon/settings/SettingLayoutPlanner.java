package com.github.epsilon.settings;

import com.github.epsilon.settings.impl.BlockListSetting;
import com.github.epsilon.settings.impl.ButtonSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.KeybindSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 根据设置声明顺序、名称语义和控件类型自动生成设置区块。
 * GUI 只消费这里输出的 Section，不再依赖 Setting 本身携带分组状态。
 */
public final class SettingLayoutPlanner {

    private static final int INLINE_SETTING_LIMIT = 4;
    private static final int SINGLE_SECTION_COLLAPSE_LIMIT = 8;
    private static final Map<String, Boolean> COLLAPSED_STATES = new HashMap<>();

    private SettingLayoutPlanner() {
    }

    public static List<Section> plan(List<Setting<?>> settings) {
        return plan(inferOwnerKey(settings), settings);
    }

    public static List<Section> plan(String ownerKey, List<Setting<?>> settings) {
        if (settings == null || settings.isEmpty()) {
            return List.of();
        }

        List<Setting<?>> sanitized = settings.stream().filter(setting -> setting != null).toList();
        if (sanitized.isEmpty()) {
            return List.of();
        }
        if (sanitized.size() <= INLINE_SETTING_LIMIT) {
            return List.of(Section.inline(ownerKey + ":inline", sanitized));
        }

        List<MutableSection> mutableSections = new ArrayList<>();
        MutableSection current = null;
        for (Setting<?> setting : sanitized) {
            if (setting.isRootSetting()) {
                current = null;
                mutableSections.add(new MutableSection("", new ArrayList<>(List.of(setting))));
                continue;
            }

            String title = inferTitle(setting);
            if (current == null || !current.title().equals(title)) {
                current = new MutableSection(title);
                mutableSections.add(current);
            }
            current.settings().add(setting);
        }

        if (mutableSections.size() == 1 && sanitized.size() < SINGLE_SECTION_COLLAPSE_LIMIT) {
            return List.of(Section.inline(ownerKey + ":inline", sanitized));
        }

        List<Section> sections = new ArrayList<>();
        Map<String, Integer> titleCounts = new HashMap<>();
        for (MutableSection mutableSection : mutableSections) {
            List<Setting<?>> sectionSettings = List.copyOf(mutableSection.settings());
            if (sectionSettings.isEmpty()) {
                continue;
            }

            String title = mutableSection.title();
            boolean collapsible = !title.isBlank() && (sectionSettings.size() > 1 || mutableSections.size() > 1);
            String baseKey = ownerKey + ":" + normalizeKey(title);
            int index = titleCounts.merge(baseKey, 1, Integer::sum);
            String key = index == 1 ? baseKey : baseKey + ":" + index;
            sections.add(new Section(key, title, sectionSettings, collapsible));
        }
        return sections;
    }

    public static long signature(List<Setting<?>> settings) {
        return signature(inferOwnerKey(settings), settings);
    }

    public static long signature(String ownerKey, List<Setting<?>> settings) {
        long signature = 23L;
        for (Section section : plan(ownerKey, settings)) {
            signature = signature * 31L + section.key().hashCode();
            signature = signature * 31L + section.title().hashCode();
            signature = signature * 31L + (section.hasHeader() ? 1 : 0);
            signature = signature * 31L + (section.isCollapsed() ? 1 : 0);
            for (Setting<?> setting : section.settings()) {
                signature = signature * 31L + setting.getName().hashCode();
            }
        }
        return signature;
    }

    private static String inferOwnerKey(List<Setting<?>> settings) {
        if (settings == null || settings.isEmpty()) {
            return "settings:empty";
        }
        Setting<?> first = settings.getFirst();
        Setting<?> last = settings.getLast();
        return "settings:" + System.identityHashCode(first) + ":" + System.identityHashCode(last) + ":" + settings.size();
    }

    private static String inferTitle(Setting<?> setting) {
        String name = setting.getName();
        String lower = name.toLowerCase(Locale.ROOT);

        if (isSelectionName(lower)) return "Selection";
        if (startsWithAny(lower, "force place")) return "Force Place";
        if (startsWithAny(lower, "place ", "packet place", "spam place")) return "Place";
        if (startsWithAny(lower, "break ", "packet break", "2b2t", "anti weakness", "swap ")) return "Break";
        if (containsAny(lower, "suicide", "wall range", "predict", "damage priority", "armor mode",
                "lethal", "safe ", "colliding crystal", "calculation")) return "Calculation";
        if (startsWithAny(lower, "render ", "swing ", "filled ", "outline alpha", "outline width", "moving ", "fade ")
                || containsAny(lower, "hud info")) return "Render";
        if (containsAny(lower, "fire flies") || lower.startsWith("ff ")) return "Fire Flies";
        if (isPathName(lower)) return "Path";
        if (containsAny(lower, "record", "save interval")) return "Record";
        if (setting instanceof ColorSetting || containsAny(lower, "color", "outline", "fill")) return "Colors";
        if (isAppearanceName(lower)) return "Appearance";
        if (isNotificationName(lower)) return "Notification";
        if (isAntiCheatName(lower)) return "Anti Cheat";
        if (setting instanceof ButtonSetting) return "Actions";
        if (setting instanceof KeybindSetting || setting instanceof BlockListSetting) return "General";
        return "General";
    }

    private static boolean isSelectionName(String lower) {
        return switch (lower) {
            case "hands", "players", "self", "friends", "crystals", "chests",
                    "creatures", "monsters", "ambients", "others", "mobs", "animals" -> true;
            default -> false;
        };
    }

    private static boolean isPathName(String lower) {
        return startsWithAny(lower, "auto launch", "spiral ", "points per", "arrival ",
                "rotation speed", "takeoff ");
    }

    private static boolean isAppearanceName(String lower) {
        return containsAny(lower, "theme", "title", "mainmenu", "main menu", "welcome",
                "render scale", "font", "minecraft font");
    }

    private static boolean isNotificationName(String lower) {
        return containsAny(lower, "notify", "chat ", "prefix");
    }

    private static boolean isAntiCheatName(String lower) {
        return containsAny(lower, "bypass", "crosshair", "hide mode");
    }

    private static boolean startsWithAny(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeKey(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }

    private record MutableSection(String title, List<Setting<?>> settings) {
        private MutableSection(String title) {
            this(title, new ArrayList<>());
        }
    }

    public record Section(String key, String title, List<Setting<?>> settings, boolean hasHeader) {

        private static Section inline(String key, List<Setting<?>> settings) {
            return new Section(key, "", List.copyOf(settings), false);
        }

        public boolean isCollapsed() {
            return hasHeader && COLLAPSED_STATES.getOrDefault(key, true);
        }

        public void toggleCollapsed() {
            if (hasHeader) {
                COLLAPSED_STATES.put(key, !isCollapsed());
            }
        }
    }

}
