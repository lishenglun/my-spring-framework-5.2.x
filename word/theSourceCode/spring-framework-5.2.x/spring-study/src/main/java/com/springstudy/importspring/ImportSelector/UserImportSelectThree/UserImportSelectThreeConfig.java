package com.springstudy.importspring.ImportSelector.UserImportSelectThree;

import org.springframework.context.annotation.Bean;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/23 8:23 下午
 */
@UserThreeAnnotation
public class UserImportSelectThreeConfig {

	/**
	 * 注意，以这种方式注册的bean，其beanName是方法名称，也就是"getUserDaoImpl"
	 * @return
	 */
	@Bean
	public UserThreeDaoImpl getUserDaoImpl() {
		return new UserThreeDaoImpl();
	}

}