package com.eligesoft.orditogram.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Attività periodiche di Orditogram (es. consegna dei webhook e pulizia delle consegne vecchie). */
@Configuration
@EnableScheduling
public class Pianificazione {
}
