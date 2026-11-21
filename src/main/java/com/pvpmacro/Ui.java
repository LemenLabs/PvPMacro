
package com.pvpmacro;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class Ui {
    private Ui() {}

    static Object button(Object screen, String text, int x, int y, int w, int h, Runnable action) {
        try {
            Class<?> button = Class.forName("net.minecraft.client.gui.components.Button");
            Class<?> onPress = Class.forName("net.minecraft.client.gui.components.Button$OnPress");

            Object handler = Proxy.newProxyInstance(
                onPress.getClassLoader(),
                new Class<?>[]{onPress},
                (proxy, method, args) -> {
                    if (method.getName().equals("onPress")) {
                        try {
                            action.run();
                        } catch (RuntimeException e) {
                            PvpLog.info("menu action failed: " + e);
                        }
                        return null;
                    }
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) return false;
                    if (r == int.class) return 0;
                    return null;
                }
            );

            Object builder = button.getMethod("builder", Component.class, onPress)
                .invoke(null, Component.literal(text), handler);
            builder = Reflect.invoke(builder, "bounds", x, y, w, h);
            Object widget = Reflect.invoke(builder, "build");
            add(screen, widget);
            return widget;

        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot create a menu button: " + e, e);
        }
    }

    private static void add(Object screen, Object widget) throws ReflectiveOperationException {
        for (Class<?> c = screen.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals("addRenderableWidget")
                    && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isInstance(widget)) {
                    m.setAccessible(true);
                    m.invoke(screen, widget);
                    return;
                }
            }
        }
        throw new NoSuchMethodException("addRenderableWidget on " + screen.getClass().getName());
    }

    static void clear(Object screen) {
        try {
            for (Class<?> c = screen.getClass(); c != null; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals("clearWidgets") && m.getParameterCount() == 0) {
                        m.setAccessible(true);
                        m.invoke(screen);
                        return;
                    }
                }
            }
            PvpLog.info("menu: clearWidgets not found");
        } catch (ReflectiveOperationException e) {
            PvpLog.info("menu: clearWidgets failed: " + e);
        }
    }

    static void setLabel(Object widget, String text) {
        try {
            Reflect.invoke(widget, "setMessage", Component.literal(text));
        } catch (ReflectiveOperationException e) {
            PvpLog.info("menu: setMessage failed: " + e);
        }
    }

    static void setActive(Object widget, boolean active) {
        try {
            widget.getClass().getField("active").setBoolean(widget, active);
        } catch (ReflectiveOperationException | RuntimeException e) {
            PvpLog.info("menu: cannot set active: " + e);
        }
    }

    static void setScreen(Minecraft mc, Screen screen) {
        for (Class<?> c = mc.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isAssignableFrom(Screen.class)
                    && m.getName().toLowerCase().contains("screen")
                    && !m.getName().toLowerCase().startsWith("get")) {
                    try {
                        m.setAccessible(true);
                        m.invoke(mc, screen);
                        return;
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        PvpLog.info("menu: screen method failed: " + e);
                    }
                }
            }
        }
        PvpLog.info("menu: could not find a screen method");
    }
}
