package com.tiptop.app;

import org.junit.Test;
import java.util.Collections;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Set;
import static org.junit.Assert.*;

public class AppFilterTest {
    private final Set<String> selected = new HashSet<>(Arrays.asList("com.example.reader", "com.tiptop.app"));

    @Test public void allAppsIgnoresSavedLists() {
        assertTrue(AppFilter.allows(AppFilter.ALL, selected, "com.example.reader"));
        assertTrue(AppFilter.allows(AppFilter.ALL, selected, "com.other.app"));
    }
    @Test public void blacklistExcludesOnlyExactPackages() {
        assertFalse(AppFilter.allows(AppFilter.BLACKLIST, selected, "com.example.reader"));
        assertTrue(AppFilter.allows(AppFilter.BLACKLIST, selected, "com.example.reader.beta"));
        assertTrue(AppFilter.allows(AppFilter.BLACKLIST, Collections.emptySet(), "com.example.reader"));
    }
    @Test public void whitelistAllowsOnlySelectedApps() {
        assertTrue(AppFilter.allows(AppFilter.WHITELIST, selected, "com.example.reader"));
        assertFalse(AppFilter.allows(AppFilter.WHITELIST, selected, "com.other.app"));
        assertFalse(AppFilter.allows(AppFilter.WHITELIST, Collections.emptySet(), "com.example.reader"));
    }
    @Test public void unknownForegroundCannotBypassFilters() {
        assertFalse(AppFilter.allows(AppFilter.BLACKLIST, selected, null));
        assertFalse(AppFilter.allows(AppFilter.WHITELIST, selected, ""));
    }
    @Test public void tiptopObeysSameRules() {
        assertFalse(AppFilter.allows(AppFilter.BLACKLIST, selected, "com.tiptop.app"));
        assertFalse(AppFilter.allows(AppFilter.WHITELIST, Collections.emptySet(), "com.tiptop.app"));
        assertTrue(AppFilter.allows(AppFilter.WHITELIST, selected, "com.tiptop.app"));
    }
    @Test public void modesKeepSeparateLists() {
        assertNotEquals(AppFilter.listKey(AppFilter.BLACKLIST), AppFilter.listKey(AppFilter.WHITELIST));
    }
}
