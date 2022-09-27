package com.springstudy.config;

import com.springstudy.Initializethebean.obj.Bean3_One;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

/**
 * 实例化三种bean
 */
@Configuration
@ImportResource("spring-3bean-config.xml")
public class Bean3Config_One {



}