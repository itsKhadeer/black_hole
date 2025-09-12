package com.blackhole;

import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

public class Camera {
    public Vector3f target = new Vector3f(0.0f, 0.0f, 0.0f);
    public float radius = 6.34194e10f;
    public float minRadius = 1e10f, maxRadius = 1e12f;

    public float azimuth = 0.0f;
    public float elevation = (float) Math.PI / 2.0f;

    public float orbitSpeed = 0.01f;
    public double zoomSpeed = 25e9;

    public boolean dragging = false;
    public boolean moving = false;
    private double lastX = 0.0, lastY = 0.0;

    private boolean scrolling = false; // Add a flag for scrolling
    private double lastScrollTime = 0;


    public Vector3f getPosition() {
        float clampedElevation = Math.max(0.01f, Math.min(elevation, (float) Math.PI - 0.01f));
        return new Vector3f(
                (float) (radius * Math.sin(clampedElevation) * Math.cos(azimuth)),
                (float) (radius * Math.cos(clampedElevation)),
                (float) (radius * Math.sin(clampedElevation) * Math.sin(azimuth))
        );
    }

    public void update() {
        // If we scrolled in the last fraction of a second, consider it "moving"
        if (glfwGetTime() - lastScrollTime < 0.2) {
            scrolling = true;
        } else {
            scrolling = false;
        }
        moving = dragging || scrolling;
    }

    public void setupCallbacks(long window) {
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {


            if (button == GLFW_MOUSE_BUTTON_LEFT || button == GLFW_MOUSE_BUTTON_MIDDLE) {
                if (action == GLFW_PRESS) {
                    dragging = true;
                    double[] x = new double[1];
                    double[] y = new double[1];
                    glfwGetCursorPos(win, x, y);
                    lastX = x[0];
                    lastY = y[0];
                } else if (action == GLFW_RELEASE) {
                    dragging = false;
                }
                update();
            }
            if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                if (action == GLFW_PRESS) {
                    BlackHoleSim.GRAVITY_ENABLED = true;
                } else if (action == GLFW_RELEASE) {
                    BlackHoleSim.GRAVITY_ENABLED = false;
                }
            }
        });

        glfwSetCursorPosCallback(window, (win, x, y) -> {
            if (!dragging) return;
            float dx = (float) (x - lastX);
            float dy = (float) (y - lastY);

            azimuth += dx * orbitSpeed;
            elevation -= dy * orbitSpeed;
            elevation = Math.max(0.01f, Math.min(elevation, (float) Math.PI - 0.01f));

            lastX = x;
            lastY = y;
            update();
        });

        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            radius -= yoffset * zoomSpeed;
            radius = Math.max(minRadius, Math.min(radius, maxRadius));
            lastScrollTime = glfwGetTime(); // Record the time of the scroll
             update();
        });

        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS && key == GLFW_KEY_G) {
                BlackHoleSim.GRAVITY_ENABLED = !BlackHoleSim.GRAVITY_ENABLED;
                System.out.println("[INFO] Gravity turned " + (BlackHoleSim.GRAVITY_ENABLED ? "ON" : "OFF"));
            }
        });
    }
}