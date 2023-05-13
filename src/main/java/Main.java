import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class Main {

    static HashMap<Integer, String> dataBaseChannels = new HashMap<>();
    static int integrationTime = 20; //msec
    static String outputPath;

    public static void main(String[] args) {

        try {

            //search our location
            String currentJar = Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            String currentDir = new File(currentJar).getParent();

            //watch for settings file
            if (args.length>0){
               try {
                   File settings = new File(currentDir+File.separator+args[0]);
                   BufferedReader settingsStream = new BufferedReader(new FileReader(settings));
                   String[] param;
                   while (settingsStream.ready()){
                       param=settingsStream.readLine().split("=");
                       if (param[0].toLowerCase().contains("lcard")&&param[0].toLowerCase().contains("time")){
                           integrationTime=Integer.parseInt(param[1]);
                       }
                       if (param[0].matches("[0-9]+")){
                           dataBaseChannels.put(Integer.parseInt(param[0]),param[1]);
                       }
                   }
               }
               catch (Exception e){e.printStackTrace();}
            }

            //create output dir
            File dir = new File(currentDir + File.separator+"data");
            dir.mkdir();
            new File(dir.getAbsolutePath()+File.separator +"LCard").mkdir();
            new File(dir.getAbsolutePath()+File.separator +"Avantes").mkdir();

            dir.mkdir();
            outputPath = dir.getAbsolutePath() + File.separator;

            for (String filePath : searching(new File(currentDir))) {
                if (filePath.contains("sample")) {
                    File bootFile = new File(filePath);
                    BufferedReader BLOBStream = new BufferedReader(new FileReader(bootFile));
                    dataPaprser(BLOBStream);
                    BLOBStream.close();
                }
            }
        }
        catch (Exception e) {e.printStackTrace();}
        System.out.println("Finished!");
    }


    public static void dataPaprser(BufferedReader  BLOBStream ) throws IOException {

        if (!dataBaseChannels.containsKey(17)) dataBaseChannels.put(17, "Rogowski coil tor.curr.");
        if (!dataBaseChannels.containsKey(18)) dataBaseChannels.put(18, "Integrated Rogowski coil tor.curr");
        if (!dataBaseChannels.containsKey(19)) dataBaseChannels.put(19, "Loop voltage");
        if (!dataBaseChannels.containsKey(20)) dataBaseChannels.put(20, "Rogowski coil ind.curr.");
        if (!dataBaseChannels.containsKey(21)) dataBaseChannels.put(21, "Rogowski coil cham.curr.");
        if (!dataBaseChannels.containsKey(23)) dataBaseChannels.put(23, "Photodiode");
        if (!dataBaseChannels.containsKey(28)) dataBaseChannels.put(28, "Forward_microwave_power");
        if (!dataBaseChannels.containsKey(29)) dataBaseChannels.put(29, "Reflected_microwave_power");
        //dataBaseChannels.put(51, "Wavelength, nm");
        //dataBaseChannels.put(52, "Intensity, counts");

        HashMap<String, double[]> Wavelengths = new HashMap<>();
        HashMap<String, double[]> Intensities = new HashMap<>();

        String[] dataColumns;

        //read useless header
       BLOBStream.readLine();


        while (BLOBStream.ready()) {
            try {
                dataColumns = BLOBStream.readLine().split(";");

                //remove first INT with count of elements
                dataColumns[dataColumns.length - 1] = dataColumns[dataColumns.length - 1].substring(10);
                String timeValue;
                int elementNumber = dataColumns[dataColumns.length - 1].length() / 16;
                //limit amount of digits in the output
                //new BigDecimal().setScale(4, RoundingMode.UP)
                if (dataColumns[0].contains("51") || dataColumns[0].contains("52")) {

                    // for Avantes Data
                        try {
                            double[] AvantesArray = new double[elementNumber];
                            for (int i = 0; i < elementNumber; i++) {
                                AvantesArray[i] = Double.longBitsToDouble(parseUnsignedHex(dataColumns[dataColumns.length - 1].substring(i * 16, (i + 1) * 16)));
                            }
                            if (dataColumns[0].contains("51")) Wavelengths.put(dataColumns[1], AvantesArray);
                            if (dataColumns[0].contains("52")) Intensities.put(dataColumns[1], AvantesArray);
                        } catch (Exception ignored) {
                            //ignored.printStackTrace();
                            //because some spectra are empty. Causes OutOfBondsException...}
                        }
                } else
                {
                    // for L-Card data
                    String outputFileName = dataBaseChannels.get(Integer.parseInt(dataColumns[0])) + "_" + dataColumns[1];
                    outputFileName = outputFileName.replace(".", "_").replace(":", "_");
                    File outputFile = new File(outputPath +"LCard"+File.separator+outputFileName + ".dat");

                    outputFile.createNewFile(); // if file already exists will do nothing
                    OutputStream outputStream = new FileOutputStream(outputFile, false);
                    //write header
                    outputStream.write(("time,ms   " + dataBaseChannels.get(Integer.parseInt(dataColumns[0])) + ", V\n").getBytes());
                    outputStream.write((outputFileName + "\n").getBytes());
                    for (int i = 0; i < elementNumber; i++) {
                        timeValue = new BigDecimal(Double.parseDouble(i * integrationTime + "") / elementNumber).setScale(4, RoundingMode.UP) + " "; //ms
                        timeValue += new BigDecimal(Double.longBitsToDouble(parseUnsignedHex(dataColumns[dataColumns.length - 1].substring(i * 16, (i + 1) * 16)))).setScale(4, RoundingMode.UP) + "\n";
                        outputStream.write(timeValue.getBytes());
                    }
                    outputStream.close();
                    System.out.println(outputFileName + " is written");
                }
            }
            catch (Exception e){
                System.out.println("-------------------------------------------------");
                System.out.println("Parse error (most likely the array is empty): "+e.toString());
            }
        }

        System.out.println("Starting writing Avantes Spectra");
        //write Avantes spectra from arrays

        for(String date: Intensities.keySet() ){
            String outputFileName = "Avantes_" + date.replace(":", "_").replace(".", "_");
            File outputFile = new File(outputPath + "Avantes"+File.separator+outputFileName + ".dat");
            outputFile.createNewFile(); // if file already exists will do nothing
            OutputStream outputStream = new FileOutputStream(outputFile, false);

            //create header
            outputStream.write((outputFileName+"\n").getBytes());
            outputStream.write("wavelength, nm     Intensity, counts\n".getBytes());

            double[] wavelengthArray = Wavelengths.get(date);
            double[] intensitiesArray = Intensities.get(date);

            for (int i=0; i<wavelengthArray.length; i++){
                //limit amount of digits in the output
                //new BigDecimal().setScale(4, RoundingMode.UP)
                outputStream.write((new BigDecimal(wavelengthArray[i]).setScale(4, RoundingMode.UP)+
                        " "+new BigDecimal(intensitiesArray[i]).setScale(4, RoundingMode.UP)+"\n").getBytes());
            }
            outputStream.close();
            System.out.println(outputFileName+"is written");
        }


    }

    public static long parseUnsignedHex(String text) {
        if (text.length() == 16) {
            return (parseUnsignedHex(text.substring(0, 1)) << 60)
                    | parseUnsignedHex(text.substring(1));
        }
        return Long.parseLong(text, 16);
    }


    private static List<String> searching(File rootDir) {
        List<String> result = new ArrayList<>();

        LinkedList<File> dirList = new LinkedList<>();
        if (rootDir.isDirectory()) {
            dirList.addLast(rootDir);
        }

        while (dirList.size() > 0) {
            File[] filesList = dirList.getFirst().listFiles();
            if (filesList != null) {
                for (File path : filesList) {
                    if (path.isDirectory()) {
                        //wouldn't watch inside folders
                        //dirList.addLast(path);
                    } else {
                        String simpleFileName = path.getName();

                        if (simpleFileName.endsWith(".csv") || simpleFileName.endsWith(".txt")) {
                            result.add(path.getAbsolutePath());
                        }
                    }
                }
            }
            dirList.removeFirst();
        }
        return result;
    }
}