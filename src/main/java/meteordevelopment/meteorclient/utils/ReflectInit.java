/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.ScanResult;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.AddonManager;
import meteordevelopment.meteorclient.addons.MeteorAddon;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class ReflectInit {
    private static final List<String> addonPackages = new ArrayList<>();
    private static ScanResult cachedScanResult;
    private static final Map<Class<? extends Annotation>, Set<MethodInfo>> cachedMethods = new HashMap<>();
    private static final Map<MethodInfo, Method> lazyMethodCache = new HashMap<>();

    private ReflectInit() {
    }

    public static void registerPackages() {
        for (MeteorAddon addon : AddonManager.ADDONS) {
            try {
                add(addon);
            } catch (AbstractMethodError e) {
                throw new RuntimeException("Addon \"%s\" is too old and cannot be ran.".formatted(addon.name), e);
            }
        }

        if (cachedScanResult != null) {
            cachedScanResult.close();
            cachedScanResult = null;
        }
        cachedMethods.clear();
        lazyMethodCache.clear();
    }

    private static void add(MeteorAddon addon) {
        String pkg = addon.getPackage();
        if (pkg == null || pkg.isBlank()) return;
        addonPackages.add(pkg);
    }

    public static void init(Class<? extends Annotation> annotation) {
        if (addonPackages.isEmpty()) return;

        Set<MethodInfo> initTasks = getMethodsAnnotatedWith(annotation);
        if (initTasks.isEmpty()) return;

        Map<String, List<MethodInfo>> byClassName = initTasks.stream()
            .collect(Collectors.groupingBy(mi -> mi.getClassInfo().getName()));

        Set<MethodInfo> left = new HashSet<>(initTasks);

        for (MethodInfo m; (m = left.stream().findAny().orElse(null)) != null; ) {
            reflectInit(m, annotation, left, byClassName);
        }
    }

    private static <T extends Annotation> void reflectInit(MethodInfo methodInfo, Class<T> annotation, Set<MethodInfo> left, Map<String, List<MethodInfo>> byClassName) {
        left.remove(methodInfo);

        Method method = lazyMethodCache.computeIfAbsent(methodInfo, MethodInfo::loadClassAndGetMethod);

        for (String className : getDependenciesNames(method, annotation)) {
            for (MethodInfo mi : byClassName.getOrDefault(className, Collections.emptyList())) {
                if (left.contains(mi)) {
                    reflectInit(mi, annotation, left, byClassName);
                }
            }
        }

        try {
            method.invoke(null);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Error running @%s task '%s.%s'"
                .formatted(annotation.getSimpleName(), method.getDeclaringClass().getSimpleName(), method.getName()), e);
        } catch (NullPointerException e) {
            throw new RuntimeException("Method \"%s\" using Init annotations from non-static context"
                .formatted(method.getName()), e);
        }
    }

    private static <T extends Annotation> String[] getDependenciesNames(Method method, Class<T> annotation) {
        Annotation ann = method.getAnnotation(annotation);
        return switch (ann) {
            case PreInit pre -> Arrays.stream(pre.dependencies()).map(Class::getName).toArray(String[]::new);
            case PostInit post -> Arrays.stream(post.dependencies()).map(Class::getName).toArray(String[]::new);
            case null, default -> new String[0];
        };
    }

    private static ScanResult getScanResult() {
        if (cachedScanResult != null) return cachedScanResult;

        long start = System.currentTimeMillis();

        cachedScanResult = new ClassGraph()
            .enableMethodInfo()
            .enableAnnotationInfo()
            .acceptPackages(addonPackages.toArray(String[]::new))
            .scan();

        long elapsed = System.currentTimeMillis() - start;
        MeteorClient.LOG.info(
            "ClassGraph initial scan took {} ms, scanned {} packages, found {} classes",
            elapsed,
            addonPackages.size(),
            cachedScanResult.getAllClasses().size()
        );

        return cachedScanResult;
    }

    private static Set<MethodInfo> getMethodsAnnotatedWith(Class<? extends Annotation> annotation) {
        if (cachedMethods.containsKey(annotation)) return cachedMethods.get(annotation);

        ScanResult scanResult = getScanResult();
        Set<MethodInfo> result = new HashSet<>();

        long start = System.currentTimeMillis();

        scanResult.getClassesWithMethodAnnotation(annotation)
            .forEach(classInfo -> result.addAll(classInfo.getDeclaredMethodInfo()
                .filter(methodInfo -> methodInfo.hasAnnotation(annotation))));

        long elapsed = System.currentTimeMillis() - start;
        MeteorClient.LOG.info(
            "ClassGraph took {} ms to collect methods with @{}, found {} classes with {} annotated methods",
            elapsed,
            annotation.getSimpleName(),
            scanResult.getClassesWithMethodAnnotation(annotation).size(),
            result.size()
        );

        cachedMethods.put(annotation, result);

        return result;
    }
}
