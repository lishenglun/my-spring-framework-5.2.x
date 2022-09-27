package com.springstudy.importspring.ImportSelector.UserImportSelectTwo;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2020/8/23 8:08 下午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(UserImportSelectTwo.class)
public @interface UserTwoAnnotation {

}