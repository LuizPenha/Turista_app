package br.ufma.lsdi;

public class Evento {
    private double latt, lngt, latg, lngg;

    public Evento(double latt, double lngt, double latg, double lngg) {
        this.latt = latt;
        this.lngt = lngt;
        this.latg = latg;
        this.lngg = lngg;
    }

    public double getLatt() {
        return latt;
    }
    public void setLatt(double latt) {
        this.latt = latt;
    }
    public double getLngt() {
        return lngt;
    }
    public void setLngt(double lngt) {
        this.lngt = lngt;
    }

    public double getLatg() {
        return latg;
    }
    public void setLatg(double latg) {
        this.latg = latg;
    }
    public double getLngg() {
        return lngg;
    }
    public void setLngg(double lngg) {
        this.lngg = lngg;
    }
}
