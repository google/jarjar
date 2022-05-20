/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tonicsystems.jarjar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * A prefix trie of {@link Wildcard}, where the prefix is obtained from
 * {@link Wildcard#getPlainTextPrefix()}.
 *
 * This allows quick lookup of applicable wildcards in the common case where wildcards have a
 * non-empty plain-text prefix.
 */
public class WildcardTrie {
    private final TreeMap<String, WildcardTrie> subTries = new TreeMap<>();
    private final List<Wildcard> wildcards = new ArrayList<>();
    private final String prefix;

    public WildcardTrie(List<Wildcard> wildcards) {
        this("");
        final ArrayList<Wildcard> lst = new ArrayList<>(wildcards);
        // Sort values to ensure that wildcards that prefix others are added first
        lst.sort(Comparator.comparing(Wildcard::getPlainTextPrefix));
        for (Wildcard w : lst) {
            final String prefix = w.getPlainTextPrefix();
            final WildcardTrie prefixTrie = findSubTrieWhichPrefixes(prefix, this);
            if (prefixTrie.prefix.equals(prefix)) {
                prefixTrie.wildcards.add(w);
            } else {
                final WildcardTrie newTrie = new WildcardTrie(prefix);
                newTrie.wildcards.add(w);
                prefixTrie.subTries.put(prefix, newTrie);
            }
        }
    }

    private WildcardTrie(String prefix) {
        this.prefix = prefix;
    }

    private static WildcardTrie findSubTrieWhichPrefixes(String value, WildcardTrie baseTrie) {
        final String possiblePrefix = baseTrie.subTries.floorKey(value);
        // Because each level of the trie does not contain keys that are prefixes of each other,
        // there can be at most one prefix of the value at that level, and that prefix will be the
        // highest key ordered before the value (any non-prefix key would have a character
        // difference with the prefix and so be ordered before the prefix or after the value).
        if (possiblePrefix != null && value.startsWith(possiblePrefix)) {
            return findSubTrieWhichPrefixes(value, baseTrie.subTries.get(possiblePrefix));
        }
        return baseTrie;
    }

    public List<Wildcard> getPossibleMatches(String value) {
        WildcardTrie baseTrie = this;
        List<Wildcard> prefixMatches = wildcards.isEmpty()
                // If there's no match, don't even allocate a list and use the singleton emptyList
                ? Collections.emptyList() : new ArrayList<>(wildcards);
        while (true) {
            final String possiblePrefix = baseTrie.subTries.floorKey(value);
            if (possiblePrefix != null && value.startsWith(possiblePrefix)) {
                baseTrie = baseTrie.subTries.get(possiblePrefix);
                if (prefixMatches.isEmpty()) {
                    prefixMatches = new ArrayList<>(baseTrie.wildcards);
                } else {
                    prefixMatches.addAll(baseTrie.wildcards);
                }
            } else {
                prefixMatches.sort(Comparator.comparing(Wildcard::getRuleIndex));
                return prefixMatches;
            }
        }
    }
}
