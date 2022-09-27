package com.springstudy.config;

import com.springstudy.anno.ImportAwareAnnotation;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/29 11:18 下午
 */
@Configuration
@ComponentScan("com.springstudy")
@ImportAwareAnnotation
public class ImportAwareTestConfiguration {


}