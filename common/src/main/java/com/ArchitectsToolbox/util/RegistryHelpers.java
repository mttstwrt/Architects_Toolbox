package com.ArchitectsToolbox.util;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

import java.util.AbstractMap;
import java.util.Map;

// Iterating a registry's (id, value) pairs is one of the most version-churned
// shapes in modded MC: Registry.entrySet() vs RegistryLookup.listElements(),
// ResourceKey vs Holder.Reference, .location()/.identifier(). Wrapping it here
// keeps call sites identical across branches.
public final class RegistryHelpers {

    private RegistryHelpers() {}

    public static <T> Iterable<Map.Entry<Identifier, T>> entries(Registry<T> registry) {
        return () -> registry.entrySet().stream()
            .map(e -> (Map.Entry<Identifier, T>) new AbstractMap.SimpleImmutableEntry<>(e.getKey().identifier(), e.getValue()))
            .iterator();
    }
}
