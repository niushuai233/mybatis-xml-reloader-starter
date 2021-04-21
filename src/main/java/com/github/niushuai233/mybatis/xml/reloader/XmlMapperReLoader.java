package com.github.niushuai233.mybatis.xml.reloader;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author niushuai
 */
public class XmlMapperReLoader {

    private static final Logger log = LoggerFactory.getLogger(XmlMapperReLoader.class);

    private static final String MYBATIS_PLUS_CONFIGURATION_CLASS_NAME = "com.baomidou.mybatisplus.core.MybatisConfiguration";
    private static Integer DELAY = 5;
    private static Integer PERIOD = 3;

    private static String packageSearchPath = "classpath*:mapper/**/*.xml";
    private SqlSessionFactory sqlSessionFactory;
    private Resource[] mapperLocations;

    private HashMap<String, Long> fileMapping = new HashMap<String, Long>();
    private List<String> changedFileNameList = new ArrayList<String>();

    public XmlMapperReLoader() {
    }

    public XmlMapperReLoader(SqlSessionFactory sqlSessionFactory, String packageSearchPath, Integer delay, Integer period) {
        this.sqlSessionFactory = sqlSessionFactory;
        // mapper文件位置
        XmlMapperReLoader.packageSearchPath = StringUtils.isEmpty(packageSearchPath) ? XmlMapperReLoader.packageSearchPath : packageSearchPath;
        // 默认配置 10s 开始扫描
        XmlMapperReLoader.DELAY = null == delay ? XmlMapperReLoader.DELAY : delay;
        // 默认配置 3s一次
        XmlMapperReLoader.PERIOD = null == period ? XmlMapperReLoader.PERIOD : period;

        startThreadListener();
    }

    public void startThreadListener() {

        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);

        System.out.println("_       _________          _______                    _______ _________");
        System.out.println("( (    /|\\__   __/|\\     /|(  ____ \\|\\     /||\\     /|(  ___  )\\__   __/");
        System.out.println("|  \\  ( |   ) (   | )   ( || (    \\/| )   ( || )   ( || (   ) |   ) (");
        System.out.println("|   \\ | |   | |   | |   | || (_____ | (___) || |   | || (___) |   | |");
        System.out.println("| (\\ \\) |   | |   | |   | |(_____  )|  ___  || |   | ||  ___  |   | |");
        System.out.println("| | \\   |   | |   | |   | |      ) || (   ) || |   | || (   ) |   | |");
        System.out.println("| )  \\  |___) (___| (___) |/\\____) || )   ( || (___) || )   ( |___) (___");
        System.out.println("|/    )_)\\_______/(_______)\\_______)|/     \\|(_______)|/     \\|\\_______/");
        System.out.println("Mybatis-Xml-Reloader v1.0 CreateAt 2020-04-30");

        log.info("XmlMapperLoader Initialized");
        service.scheduleAtFixedRate(() -> readMapperXml(), DELAY, PERIOD, TimeUnit.SECONDS);
        log.info("XmlMapperLoader Scan Interval {}s After {}s", PERIOD, DELAY);
    }

    public void readMapperXml() {
        try {
            Configuration configuration = sqlSessionFactory.getConfiguration();
            // step.1 扫描文件
            try {
                this.scanMapperXml();
            } catch (IOException e) {
                throw new RuntimeException("package " + packageSearchPath + " search path error");
            }

            // 2 判断是否有文件发生了变化
            if (this.isChanged()) {
                // 2.1 清理
                this.removeConfig(configuration);
                // 2.2 重新加载
                for (org.springframework.core.io.Resource configLocation : mapperLocations) {
                    try {
                        XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(configLocation.getInputStream(), configuration, configLocation.toString(), configuration.getSqlFragments());
                        xmlMapperBuilder.parse();
                    } catch (IOException e) {
                        log.info("Mapper [" + configLocation.getFilename() + "] doesn't exist or the content format is wrong");
                        continue;
                    }
                }
            }

            for (String fileName : changedFileNameList) {
                log.info("Mapper [" + fileName + "] Reload Success");
            }

            changedFileNameList.clear();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 扫描xml文件所在的路径
     *
     * @throws IOException
     */
    private void scanMapperXml() throws IOException {
        this.mapperLocations = new PathMatchingResourcePatternResolver().getResources(packageSearchPath);
    }

    /**
     * 清空Configuration中几个重要的缓存
     *
     * @param configuration
     * @throws Exception
     */
    private void removeConfig(org.apache.ibatis.session.Configuration configuration) throws Exception {
        Class<?> classConfig = configuration.getClass();
        clearMap(classConfig, configuration, "mappedStatements");
        clearMap(classConfig, configuration, "caches");
        clearMap(classConfig, configuration, "resultMaps");
        clearMap(classConfig, configuration, "parameterMaps");
        clearMap(classConfig, configuration, "keyGenerators");
        clearMap(classConfig, configuration, "sqlFragments");

        clearSet(classConfig, configuration, "loadedResources");
    }

    @SuppressWarnings("rawtypes")
    private void clearMap(Class<?> classConfig, org.apache.ibatis.session.Configuration configuration, String fieldName) throws Exception {
        Field field = null;
        if (MYBATIS_PLUS_CONFIGURATION_CLASS_NAME.equals(configuration.getClass().getName())) {
            field = classConfig.getSuperclass().getDeclaredField(fieldName);
        } else {
            field = classConfig.getClass().getDeclaredField(fieldName);
        }
        field.setAccessible(true);
        Map mapConfig = (Map) field.get(configuration);
        mapConfig.clear();
    }

    @SuppressWarnings("rawtypes")
    private void clearSet(Class<?> classConfig, org.apache.ibatis.session.Configuration configuration, String fieldName) throws Exception {
        Field field = null;
        if (MYBATIS_PLUS_CONFIGURATION_CLASS_NAME.equals(configuration.getClass().getName())) {
            field = classConfig.getSuperclass().getDeclaredField(fieldName);
        } else {
            field = classConfig.getClass().getDeclaredField(fieldName);
        }
        field.setAccessible(true);
        Set setConfig = (Set) field.get(configuration);
        setConfig.clear();
    }

    /**
     * 判断文件是否发生了变化
     *
     * @throws IOException
     */
    private boolean isChanged() throws IOException {
        boolean flag = false;
        for (org.springframework.core.io.Resource resource : mapperLocations) {
            String resourceName = resource.getFilename();

            // 新增标识
            boolean addFlag = !fileMapping.containsKey(resourceName);

            // 修改文件 判断文件内容是否有变化
            Long compareFrame = fileMapping.get(resourceName);
            long lastFrame = resource.contentLength() + resource.lastModified();
            // 修改标识
            boolean modifyFlag = null != compareFrame && compareFrame.longValue() != lastFrame;

            // 新增或是修改时, 存储文件
            if (addFlag || modifyFlag) {
                // 文件内容帧值
                fileMapping.put(resourceName, Long.valueOf(lastFrame));
                changedFileNameList.add(resourceName);
                flag = true;
            }
        }
        return flag;
    }
}