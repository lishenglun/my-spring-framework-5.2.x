package com.springstudy.msb.other.condition;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/10/5 17:30
 */
@Configuration
@Conditional(MyCondition.class)
public class ConditionConfiguration {

}