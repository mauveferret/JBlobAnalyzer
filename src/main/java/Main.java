import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class Main {

    static int integrationTime = 20; //msec
    static String outputPath;

    public static void main(String[] args) {

        try {
            //search our location
            String currentJar = Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            String currentDir = new File(currentJar).getParent();
            //create output dir
            File dir = new File(currentDir + "\\data");
            dir.mkdir();
            outputPath = dir.getAbsolutePath()+"\\";

            for (String filePath : searching(new File(currentDir))) {
                File bootFile = new File(filePath);
                BufferedReader BLOBStream = new BufferedReader(new FileReader(bootFile));
                if (filePath.contains("lcard")) {
                    //an integration time can be a second attribute of scrypt
                    if (args.length > 0) integrationTime = Integer.parseInt(args[0]);
                    LCard(BLOBStream);
                }
                if (filePath.contains("avantes")) Avantes(BLOBStream);
                BLOBStream.close();
            }
        }
        catch (Exception e) {e.printStackTrace();}
        System.out.println("Finished!");
    }

    public static void Avantes(BufferedReader  BLOBStream ) throws IOException {
        System.out.println("Started parsing for Avantes");
        HashMap<Integer, String> AvantesChannels = new HashMap<>();
        AvantesChannels.put(51, "Wavelength, nm");
        AvantesChannels.put(52, "Intensity, counts");

        HashMap<String, double[]> Wavelengths = new HashMap<>();
        HashMap<String, double[]> Intensities = new HashMap<>();

        //read useless header
        String line;
        String[] columns;
        line = BLOBStream.readLine();


        while (BLOBStream.ready()) {
            try {
                line = BLOBStream.readLine();
                columns = line.split(";");
                //remove first INT with count of elements
                columns[2] = columns[2].substring(10);
                int elementNumber = columns[2].length() / 16;
                double[] array = new double[elementNumber];
                for (int i = 0; i < elementNumber; i++) {
                  array[i]=Double.longBitsToDouble(parseUnsignedHex(columns[2].substring(i * 16, (i + 1) * 16)));
                }
                if (columns[0].contains("51")) Wavelengths.put(columns[1], array);
                if (columns[0].contains("52")) Intensities.put(columns[1], array);
            }
            catch (Exception ignored) {
                //ignored.printStackTrace();
                //because some spectra are empty. Causes OutOfBondsException...}
        }
    }

        for(String date: Intensities.keySet() ){
            String outputFileName = "Avantes_" + date.replace(":", "_").replace(".", "_");
            File outputFile = new File(outputPath + outputFileName + ".dat");
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

    public static void LCard(BufferedReader  BLOBStream ) throws IOException {
        HashMap<Integer, String> LCardChannels = new HashMap<>();
        LCardChannels.put(17, "TorField_Rogowski_coil");
        LCardChannels.put(18, "TorField_integrated_Rogowski_coil");
        LCardChannels.put(19, "Loop_voltage");
        LCardChannels.put(23, "Photodiode");
        LCardChannels.put(28, "Forward_microwave_power");
        LCardChannels.put(29, "Reflected_microwave_power");

        String line;
        String[] columns;
        String outputFileName;
        //read header
        String header = BLOBStream.readLine();

        while (BLOBStream.ready()) {
            line = BLOBStream.readLine();
            columns = line.split(";");
            outputFileName = LCardChannels.get(Integer.parseInt(columns[0])) + "_" + columns[1];
            outputFileName=outputFileName.replace(".","_").replace(":","_");
            File outputFile = new File(outputPath + outputFileName+ ".dat");
            outputFile.createNewFile(); // if file already exists will do nothing
            OutputStream outputStream = new FileOutputStream(outputFile, false);

            //write header
            outputStream.write(("time,ms   " + LCardChannels.get(Integer.parseInt(columns[0])) + ", V\n").getBytes());
            outputStream.write((outputFileName+"\n").getBytes());
            //remove first INT with count of elements
            columns[columns.length-1] = columns[columns.length-1].substring(10);
            String timeValue;

            //limit amount of digits in the output
            //new BigDecimal().setScale(4, RoundingMode.UP)

            int elementNumber = columns[columns.length-1].length()/16;
            for (int i = 0; i < elementNumber; i++) {
                timeValue = new BigDecimal(Double.parseDouble(i*integrationTime+"")/elementNumber).setScale(4, RoundingMode.UP)+" "; //ms
                timeValue+=new BigDecimal(Double.longBitsToDouble(parseUnsignedHex(columns[columns.length-1].substring(i*16, (i+1)*16)))).setScale(4, RoundingMode.UP)+"\n";
                outputStream.write(timeValue.getBytes());
            }
            outputStream.close();
            System.out.println(outputFileName+" is written");
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

                        if (simpleFileName.endsWith(".csv")) {
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