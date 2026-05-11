package com.ArchitectsToolbox.util;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

import java.util.AbstractMap;
import java.util.Map;

// Iterating a registry's (id, value) pairs is one of the most version-churned
// shapes in modded MC: Registry.entrySet() vs RegistryLookup.listElements(),
// ResourceKey vs Holder.Reference, .location()/.location(). Wrapping it here
// keeps call sites identical across branches.
public final class RegistryHelpers {

    private RegistryHelpers() {}

    public static <T> Iterable<Map.Entry<ResourceLocation, T>> entries(Registry<T> registry) {
        return () -> registry.entrySet().stream()
            .map(e -> (Map.Entry<ResourceLocation, T>) new AbstractMap.SimpleImmutableEntry<>(e.getKey().location(), e.getValue()))
            .iterator();
    }
}
