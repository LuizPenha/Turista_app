package br.ufma.lsdi;

public class Distancia {
    public static double dist(double latt, double lngt, double latg, double lngg){

      //  return 10;
        final int R = 6371; // raio da Terra
        double latDistance = toRad(latg-latt);
        double lonDistance = toRad(lngg-lngt);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(toRad(latt)) * Math.cos(toRad(latg)) * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double distance = R * c;
        return distance;
    };

    private static double toRad(Double val) {
        return val * Math.PI / 180;
    }
}
