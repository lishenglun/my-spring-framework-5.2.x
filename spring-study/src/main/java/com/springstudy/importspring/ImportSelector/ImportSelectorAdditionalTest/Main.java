package com.springstudy.importspring.ImportSelector.ImportSelectorAdditionalTest;

import com.springstudy.importspring.ImportSelector.UserImportSelectOne.UserImportSelectOneConfig;
import com.springstudy.importspring.ImportSelector.UserImportSelectOne.UserOne;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2020/11/16 3:51 下午
 */
public class Main {


	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		// @Import(UserImportSelectOne.class)
		context.register(EnablePaasCacheConfig.class);
		context.refresh();

	}

}