package com.time.nlp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * 新版时间表达式识别的主要工作类
 * <p>
 *
 * @author <a href="mailto:kexm@corp.21cn.com">kexm</a>
 * @since 2016年5月4日
 */
public class TimeNormalizer implements Serializable {

    private static final long serialVersionUID = 463541045644656392L;
    private static final Logger LOGGER = LoggerFactory.getLogger(TimeNormalizer.class);

    private String timeBase;
    private String oldTimeBase;
    private static Pattern patterns = null;
    private String target;
    private TimeUnit[] timeToken = new TimeUnit[0];

    private boolean isPreferFuture = true;

    public TimeNormalizer() {
        if (patterns == null) {
            try {
                InputStream in = getClass().getResourceAsStream("/TimeExp.m");
                ObjectInputStream objectInputStream = new ObjectInputStream(
                        new BufferedInputStream(new GZIPInputStream(in)));
                patterns = readModel(objectInputStream);
            } catch (Exception e) {
                e.printStackTrace();
                System.err.print("Read model error!");
            }
        }
    }

    /**
     * 参数为TimeExp.m文件路径
     *
     * @param path
     */
    public TimeNormalizer(String path) {
        if (patterns == null) {
            try {
                patterns = readModel(path);
            } catch (Exception e) {
                e.printStackTrace();
                System.err.print("Read model error!");
            }
        }
    }

    /**
     * 参数为TimeExp.m文件路径
     *
     * @param path
     */
    public TimeNormalizer(String path, boolean isPreferFuture) {
        this.isPreferFuture = isPreferFuture;
        if (patterns == null) {
            try {
                patterns = readModel(path);
                LOGGER.debug("loaded pattern:{}", patterns.pattern());
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                System.err.print("Read model error!");
            }
        }
    }

    /**
     * TimeNormalizer的构造方法，根据提供的待分析字符串和timeBase进行时间表达式提取
     * 在构造方法中已完成对待分析字符串的表达式提取工作
     *
     * @param target
     *            待分析字符串
     * @param timeBase
     *            给定的timeBase
     * @return 返回值
     */
    public TimeUnit[] parse(String target, String timeBase) {
        this.target = target;
        this.timeBase = timeBase;
        this.oldTimeBase = timeBase;
        // 字符串预处理
        preHandling();
        timeToken = TimeEx(this.target, timeBase);
        return timeToken;
    }

    /**
     * 同上的TimeNormalizer的构造方法，timeBase取默认的系统当前时间
     *
     * @param target
     *            待分析字符串
     * @return 时间单元数组
     */
    public TimeUnit[] parse(String target) {
        this.target = target;
        this.timeBase = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(Calendar.getInstance().getTime());// TODO
        // Calendar.getInstance().getTime()换成new
        // Date？
        this.oldTimeBase = timeBase;
        preHandling();// 字符串预处理
        timeToken = TimeEx(this.target, timeBase);
        return timeToken;
    }

    //

    /**
     * timeBase的get方法
     *
     * @return 返回值
     */
    public String getTimeBase() {
        return timeBase;
    }

    /**
     * oldTimeBase的get方法
     *
     * @return 返回值
     */
    public String getOldTimeBase() {
        return oldTimeBase;
    }

    public boolean isPreferFuture() {
        return isPreferFuture;
    }

    public void setPreferFuture(boolean isPreferFuture) {
        this.isPreferFuture = isPreferFuture;
    }

    /**
     * timeBase的set方法
     *
     * @param s
     *            timeBase
     */
    public void setTimeBase(String s) {
        timeBase = s;
    }

    /**
     * 重置timeBase为oldTimeBase
     */
    public void resetTimeBase() {
        timeBase = oldTimeBase;
    }

    /**
     * 时间分析结果以TimeUnit组的形式出现，此方法为分析结果的get方法
     *
     * @return 返回值
     */
    public TimeUnit[] getTimeUnit() {
        return timeToken;
    }

    /**
     * 待匹配字符串的清理空白符和语气助词以及大写数字转化的预处理
     */
    private void preHandling() {
        target = stringPreHandlingModule.delKeyword(target, "\\s+"); // 清理空白符
        target = stringPreHandlingModule.delKeyword(target, "[的]+"); // 清理语气助词
        target = stringPreHandlingModule.numberTranslator(target);// 大写数字转化
        // TODO 处理大小写标点符号
    }

    /**
     * 有基准时间输入的时间表达式识别
     * <p>
     * 这是时间表达式识别的主方法， 通过已经构建的正则表达式对字符串进行识别，并按照预先定义的基准时间进行规范化
     * 将所有别识别并进行规范化的时间表达式进行返回， 时间表达式通过TimeUnit类进行定义
     *
     * @param String
     *            输入文本字符串
     * @param String
     *            输入基准时间
     * @return TimeUnit[] 时间表达式类型数组
     */
    private TimeUnit[] TimeEx(String tar, String timebase) {
        Matcher match;
        int startline = -1, endline = -1;// 每个匹配时间的起止点
        int rpointer = 0;// 计数器，记录当前识别到哪一个字符串了
        int point_s = -1;// 统计时间起始点
        String exp = null;// 记录时间字符
        List<TimeUnit> tms = new ArrayList<TimeUnit>();
        TimePoint contextTp = new TimePoint();

        match = patterns.matcher(tar);

        while (match.find()) {
            startline = match.start();
            if (endline == startline) // 假如下一个识别到的时间字段和上一个是相连的 @author kexm
            {
                rpointer--;
                exp += match.group();// 则把下一个识别到的时间字段加到上一个时间字段去
            } else {
                if (rpointer > 0) {
                    boolean flag = false;
                    if (rpointer > 1) {
                        int fg = tms.get(tms.size() - 1).getEnd();
                        flag = tar.substring(fg, point_s).matches("^[到至]$") ? true : false;
                    }
                    tms.add(new TimeUnit(exp, point_s, endline, flag, this, contextTp));
                }
                exp = match.group();// 记录当前识别到的时间字段，并把startmark开关关闭。这个开关貌似没用？
                point_s = startline;
            }
            endline = match.end();
            rpointer++;
        }
        boolean flag = false;
        if (rpointer > 1) {
            int fg = tms.get(tms.size() - 1).getEnd();
            flag = tar.substring(fg, point_s).matches("^[到至]$") ? true : false;
        }
        tms.add(new TimeUnit(exp, point_s, endline, flag, this, contextTp));
        /** 过滤无法识别的字段 */
        return filterTimeUnit(tms);
    }

    /**
     * 过滤timeUnit中无用的识别词。无用识别词识别出的时间是1970.01.01 00:00:00(fastTime=-28800000)
     *
     * @param timeUnit
     * @return
     */
    public static TimeUnit[] filterTimeUnit(List<TimeUnit> list) {
        if (null == list || list.isEmpty()) {
            return null;
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).getTime().getTime() == -28800000) {
                list.remove(i);
            }
        }
        TimeUnit[] newT = new TimeUnit[list.size()];
        newT = list.toArray(newT);
        return newT;
    }

    private Pattern readModel(String file) throws Exception {
        ObjectInputStream in;
        if (file.startsWith("jar:file") || file.startsWith("file:")) {
            in = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new URL(file).openStream())));
        } else {
            in = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))));
        }
        return readModel(in);
    }

    private Pattern readModel(ObjectInputStream in) throws Exception {
        Pattern p = (Pattern) in.readObject();
        LOGGER.debug("model pattern:{}", p.pattern());
        return Pattern.compile(p.pattern());
    }

    public static void writeModel(Object p, String path) throws Exception {
        ObjectOutputStream out = new ObjectOutputStream(
                new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(path))));
        out.writeObject(p);
        out.close();
    }

    public static void main(String args[]) throws Exception {

        /** 写TimeExp */

        String reg = FileUtils
                .readFileToString(new File(TimeNormalizer.class.getClassLoader().getResource("Pattern.txt").toURI()));
        System.out.println(reg);
        Pattern p = Pattern.compile(reg);
        writeModel(p, TimeNormalizer.class.getClassLoader().getResource("TimeExp.zip").getFile());
        System.out.println(TimeNormalizer.class.getClassLoader().getResource("TimeExp.zip").getFile());

        /**
         * 测试加载
         */
        TimeNormalizer normalizer = new TimeNormalizer(
                TimeNormalizer.class.getClassLoader().getResource("TimeExp.zip").getFile());
        normalizer.parse("去年第4季度至今");// 抽取时间
        TimeUnit[] unit = normalizer.getTimeUnit();
        for (TimeUnit timeUnit : unit) {
            System.out.println(timeUnit);
            System.out.println(timeUnit.getStart() + "-----" + timeUnit.getEnd());
        }

    }

}
