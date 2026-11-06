package com.github.tadaooq123.bdm.client;

import com.github.tadaooq123.bdm.Role;

/** Роль этого игрока, присланная сервером. */
public final class ClientRole {

    private static volatile Role role;

    private ClientRole() {
    }

    public static Role get() {
        return role;
    }

    public static void set(Role value) {
        role = value;
    }

    public static boolean isDeaf() {
        return role == Role.DEAF;
    }

    public static boolean isBlind() {
        return role == Role.BLIND;
    }
}
