package com.dataart.demo;

import java.io.IOException;

/**
 * Created by newbie on 03.11.16.
 */
public interface Video {
    boolean capture(String frameFileName) throws IOException;
}
