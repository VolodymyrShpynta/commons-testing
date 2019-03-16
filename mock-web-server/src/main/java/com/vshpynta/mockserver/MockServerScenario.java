package com.vshpynta.mockserver;


import java.lang.annotation.*;

/**
 * Is used to specify configuration files for mock server.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface MockServerScenario {

    String[] value() default "";
    String[] ignore() default "";

}
