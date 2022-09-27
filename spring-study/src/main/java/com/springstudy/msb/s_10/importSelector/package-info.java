package com.springstudy.msb.s_10.importSelector;

/**
 * 一、@Import、ImportSelector、DeferredImportSelector、ImportBeanDefinitionRegistrar
 *
 * 有@Import(A.class)、ImportSelector#selectImports()、DeferredImportSelector#selectImports()这三种方式可以导入类。
 * 会对这三种方式导入的类，继续进行processImports()处理，直到导入的类被当成一个ImportBeanDefinitionRegistrar、或者是@Configuration一样的配置类进行处理！
 *
 * 1、注意：只有当@Import(A.class)、ImportSelector#selectImports()、DeferredImportSelector#selectImports()导入的类，
 * 是没有实现ImportSelector、DeferredImportSelector、ImportBeanDefinitionRegistrar这三个接口的类，才算是导入的配置类！
 *
 * 例如：@Import(A.class)导入的A implements ImportSelector，A是导入的类，但是A不是导入的配置类，会继续对A进行processImports()处理，
 * 获取A#selectImports()，假设AA#selectImports()获取到的是B.class.getName()，B是导入的类，那么继续对B进行processImports()处理，
 * （1）如果B没有实现ImportSelector、DeferredImportSelector接口，但是实现了ImportBeanDefinitionRegistrar，那么就当作ImportBeanDefinitionRegistrar处理掉；
 * （2）如果B没有实现ImportSelector、DeferredImportSelector、ImportBeanDefinitionRegistrar，那就当作普通配置类处理掉！
 * （3）如果B implements ImportSelector，那么就会执行B#selectImports()，假设B#selectImports()获取到的是C.class.getName，那么继续对C进行processImports()处理，
 * 如此循环往复下去，直到selectImports()所导入的类没有实现ImportSelector、DeferredImportSelector这2个接口，才算结束
 *
 * 例如：@Import(A.class)导入的A，没有实现ImportSelector、DeferredImportSelector这2个接口。
 * （1）如果实现了ImportBeanDefinitionRegistrar接口，就当作ImportBeanDefinitionRegistrar处理掉；
 * （2）如果也没有实现ImportBeanDefinitionRegistrar接口，就当作配置类进行处理！
 *
 * 总结：最终一定是要得到一个没有实现ImportSelector、DeferredImportSelector这2个接口的类，然后进行处理：
 * （1）要么是实现了ImportBeanDefinitionRegistrar接口，当作ImportBeanDefinitionRegistrar处理；
 * （2）要是么也没有ImportBeanDefinitionRegistrar接口，当作普通配置类进行处理。
 * 只有这样才能终止processImports()递归调用！
 *
 * 二、DeferredImportSelector和ImportSelector的区别：
 * (1)如果只实现了ImportSelector接口就立即处理
 * (2)实现了DeferredImportSelector则延迟处理，等到所有配置类都处理完毕之后，再处理它
 */