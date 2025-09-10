package com.xiaoshi2022.everything_morph.util;

import java.util.UUID;

public class RandomUUIDGenerator {
    public static String generateRandomUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}