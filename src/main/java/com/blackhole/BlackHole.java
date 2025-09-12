package com.blackhole;

import org.joml.Vector3f;

public class BlackHole {
    public Vector3f position;
    public double mass;
    public double r_s; // Schwarzschild radius

    // Constants
    private static final double G = 6.67430e-11;
    private static final double C = 299792458.0;

    public BlackHole(Vector3f pos, double m) {
        this.position = pos;
        this.mass = m;
        this.r_s = 2.0 * G * mass / (C * C);
    }
}