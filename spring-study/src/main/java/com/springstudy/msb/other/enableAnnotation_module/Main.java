package com.springstudy.msb.other.enableAnnotation_module;

import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description @Enable**模块的查看。发现，@Enable**模块的本质就是使用@Import来实现的
 * @date 2022/9/25 16:33
 */
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class Main {

}