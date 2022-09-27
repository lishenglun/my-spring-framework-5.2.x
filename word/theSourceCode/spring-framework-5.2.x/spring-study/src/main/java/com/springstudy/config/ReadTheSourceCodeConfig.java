package com.springstudy.config;

import com.springstudy.Initializethebean.ReadTheSourceCode.RImportObj;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/9/2 9:25 下午
 */
@Configuration
@Import(RImportObj.class)
@ComponentScan("com.springstudy.Initializethebean.ReadTheSourceCode.obj")
@EnableAspectJAutoProxy
public class ReadTheSourceCodeConfig {



}