package com.blackhole;

import org.joml.Vector3f;
import org.joml.Vector4f;

public class ObjectData {
    public Vector4f posRadius;
    public Vector4f color;
    public float mass;
    public Vector3f velocity = new Vector3f(0.0f, 0.0f, 0.0f);

    public ObjectData(Vector4f posRadius, Vector4f color, float mass) {
        this.posRadius = posRadius;
        this.color = color;
        this.mass = mass;
    }
}