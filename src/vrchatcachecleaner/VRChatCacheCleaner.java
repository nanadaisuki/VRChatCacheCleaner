package vrchatcachecleaner;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import static javax.swing.JOptionPane.ERROR_MESSAGE;

/**
 *
 * It will read the last access time of the file and automatically delete some
 * old caches
 */
public class VRChatCacheCleaner {

    private static boolean dev = false;

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("dev")) {
            dev = true;
        } else {
            checkRunWithCmd();
        }
        Properties props = System.getProperties();
        String userFloder = props.getProperty("user.home");
        System.out.println("User floder：" + userFloder);
        File defaultCache = new File(userFloder + "\\AppData\\LocalLow\\VRChat\\VRChat\\Cache-WindowsPlayer");
        System.out.println("Cache Floder: " + defaultCache.getPath());
        File[] cacheFloder = defaultCache.listFiles();
        Map<Long, File> sort = sortFilesByAccessTime(cacheFloder, dev);
        System.out.println("Some old caches: ");
        printFiles(sort, 5);
        File[] allCacheFile = getFiles(defaultCache);
        long totalUsage = getLength(allCacheFile);
        System.out.println("You have " + cacheFloder.length + " caches, "
                + "total usage: " + humanReadableByteCountBin(totalUsage));
        System.out.println();
        System.out.println("Do you want to delete some old caches? Enter your expected total usage (GB):");
        Scanner sc = new Scanner(System.in);
        String input = sc.nextLine();
        double d = Double.parseDouble(input);
        d *= 1024 * 1024 * 1024;
        long targetUsage = (long) d;
        long aboutDelete = totalUsage - targetUsage;
        int percentage = (int) ((double) aboutDelete / totalUsage * 100);
        if (dev) {
            System.out.println("Your input " + input + " is " + targetUsage + " bytes,"
                    + " total usage " + totalUsage + " bytes so about " + aboutDelete
                    + "(" + percentage + "%) bytes will be delete.");
        }
        List<File> list = new ArrayList();
        Iterator<File> files = sort.values().iterator();
        long lengthCount = 0;
        while (totalUsage > targetUsage && files.hasNext()) {
            File f = files.next();
            list.add(f);
            long length = getLength(getFiles(f));
            totalUsage -= length;
            lengthCount += length;

        }
        System.out.println("About " + humanReadableByteCountBin(aboutDelete) + "(" + percentage + "%) will be delete.");
        Map<Long, File> delete = sortFilesByAccessTime(list.toArray(new File[list.size()]), false);
        printFiles(delete, -1);
        System.out.println();
        waitConfirm("Some files will be delete, enter \"confirm\" to continue.", "confirm");

        for (File f : list) {
            safeDelete(f, false);
        }
    }

    public static void checkRunWithCmd() {
        if (System.console() == null) {
            JOptionPane.showMessageDialog(null, "You can only run it from console, for example, enter \"java -jar VRChatCacheCleaner.jar\" in cmd ", "ERROR", ERROR_MESSAGE);
            System.exit(1);
        }
    }

    public static void safeDelete(File f, boolean useRecycleBin) {
        if (useRecycleBin) {
            return;
        } else {
            try {
                deleteAll(f);
                System.out.println(f.getName() + " has been deleted.");
            } catch (Exception e) {
                System.err.println(f.getName() + " cannot be deleted: " + e.getLocalizedMessage());
                if (e instanceof AccessDeniedException) {
                    System.err.println("You must need run the program with admin mode to delete files in disk C:");
                }
                if (dev) {
                    e.printStackTrace();
                }

            }
        }
    }

    public static void deleteAll(File f) throws IOException {
        Path path = f.toPath();
        Files.walkFileTree(path,
                new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file,
                    BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                if (dev) {
                    System.out.println(file + " has benn delete");
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir,
                    IOException exc) throws IOException {
                Files.delete(dir);
                if (dev) {
                    System.out.println(dir + " has benn delete");
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void waitConfirm(String text, String confirm) {
        System.out.println(text);
        Scanner sc = new Scanner(System.in);
        String input = sc.nextLine();
        if (input.equals(confirm)) {
            return;
        } else {
            waitConfirm(text, confirm);
        }
    }

    public static void printFiles(Map<Long, File> sort, int max) {
        boolean nolimit = false;
        if (max < 0) {
            nolimit = true;
        }
        long current = System.currentTimeMillis();
        long dayMills = 1000 * 60 * 60 * 24;
        System.out.println("    Cache name:         Last Load:");
        int count = 0;
        for (Entry<Long, File> entry : sort.entrySet()) {
            if (count < max || nolimit) {
                File file = entry.getValue();
                long time = entry.getKey();
                String days = String.valueOf((current - time) / dayMills);
                if (time == 0) {
                    days = "??";
                }
                System.out.println("    " + file.getName() + (time == 0 ? "(0) " : "    ") + days + " days ago");
                count++;
            } else {
                break;
            }
        }
    }

    public static Map<Long, File> sortFilesByAccessTime(File[] files, boolean devm) {
        Map<Long, File> map = new TreeMap(new Comparator<Long>() {
            @Override
            public int compare(Long o1, Long o2) {
                return o1.compareTo(o2);
            }
        });
        for (File f : files) {
            if (f.getName().equals("__info")) {
                continue;
            }
            map.put(getLastAccessTime(f, devm), f);
        }
        return map;
    }

    public static long getLastAccessTime(File cacheFloder, boolean devm) {
        File[] floder = cacheFloder.listFiles();
        if (floder == null || floder[0] == null || floder[0].listFiles() == null) {
            if (devm) {
                System.out.println("Cache " + cacheFloder.getName() + " is empty.");
            }
            return 0;
        } else if (floder.length > 1) {
            if (devm) {
                System.out.println("Cache " + cacheFloder.getName() + " has too much files.");
            }
        }
        File dataFile = null;
        for (File file : floder[0].listFiles()) {
            if (file.getName().equals("__data")) {
                dataFile = file;
                if (!dev);
                {
                    break;
                }
            }
        }
        if (dataFile == null) {
            return 0;
        }

        Path path = FileSystems.getDefault().getPath(dataFile.getPath());
        BasicFileAttributes attrs;

        try {
            attrs = Files.readAttributes(path, BasicFileAttributes.class
            );

        } catch (IOException ex) {
            Logger.getLogger(VRChatCacheCleaner.class
                    .getName()).log(Level.SEVERE, null, ex);
            return 0;
        }
        FileTime time = attrs.lastAccessTime();
        return time.toMillis();
    }

    public static File[] getFiles(File file) {
        List<File> list = new ArrayList();
        list.add(file);
        for (File f : file.listFiles()) {
            if (f.isDirectory()) {
                list.addAll(Arrays.asList(getFiles(f)));
            } else {
                list.add(f);
            }
        }
        return list.toArray(new File[list.size()]);
    }

    public static long getLength(File[] files) {
        long length = 0;
        for (File f : files) {
            length += f.length();
        }
        return length;
    }

    /*
    * From https://stackoverflow.com/questions/3758606/how-can-i-convert-byte-size-into-a-human-readable-format-in-java
     */
    public static String humanReadableByteCountBin(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.1f %ciB", value / 1024.0, ci.current());
    }

}
