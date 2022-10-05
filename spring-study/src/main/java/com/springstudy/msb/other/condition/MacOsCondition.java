package com.springstudy.msb.other.condition;

import com.alibaba.druid.util.StringUtils;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 判定当前系统，是否是mac os系统
 */
public class MacOsCondition implements Condition {

	// 然后再@Configuration配置类上加@Conditional(MacOsCondition.class)就可以根据判定的结果来决定是否解析配置类，以及将配置类加入到IOC容器之中！

	/**
	 * 匹配操作系统类型
	 */
	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		String osName = context.getEnvironment().getProperty("os.name");
		return StringUtils.equalsIgnoreCase(osName, "Mac OS X");
		// 如果修改为返回false，则会报错：
		// No qualifying bean of type 'com.springstudy.msb.other.condition.User' available
		//return false;
	}

}
