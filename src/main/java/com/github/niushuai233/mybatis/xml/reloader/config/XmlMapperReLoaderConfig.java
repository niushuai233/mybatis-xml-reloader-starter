package com.github.niushuai233.mybatis.xml.reloader.config;

import com.github.niushuai233.mybatis.xml.reloader.XmlMapperReLoader;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * @author niushuai
 */
@Configuration
public class XmlMapperReLoaderConfig {

    /**
     * 扫描开关 默认关闭
     */
    @Value("${mapper.reload.enable:false}")
    private String enable;

    /**
     * 初始扫描延迟 默认10s
     */
    @Value("${mapper.reload.delay:10}")
    private Integer delay;

    /**
     * 扫描时间间隔 默认3s
     */
    @Value("${mapper.reload.period:3}")
    private Integer period;

    /**
     * mapper位置 默认3s
     */
    @Value("${mapper.reload.mapperLocation:classpath*:mapper/**/*.xml}")
    private String mapperLocationPath;

    @Resource
    private SqlSessionFactory sqlSessionFactory;

    @Bean
    public XmlMapperReLoader xmlMapperReLoader() {
        if (!Boolean.TRUE.toString().equalsIgnoreCase(enable)) {
            return new XmlMapperReLoader();
        }
        return new XmlMapperReLoader(sqlSessionFactory, mapperLocationPath, delay, period);
    }
}


