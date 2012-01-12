/*
 *  Copyright (C) 2011 Michele Roncalli <roncallim at gmail dot com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package olimpo;

import exception.NoMoreValuesException;
import exception.ParameterNotFoundException;
import exception.ResultOkException;
import exception.VariableNotSetException;
import factorgraph.NodeFunction;
import factorgraph.NodeVariable;
import function.FunctionEvaluator;
import function.RelaxableFunctionEvaluator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Random;
import maxsum.Relaxable_MS_COP_Instance;
import misc.Utils;
import operation.Solver;
import system.COP_Instance;

/**
 *
 * @author Michele Roncalli <roncallim at gmail dot com>
 */
public class Eris implements Solver {

    private COP_Instance cop;
    private String op = "max";
    private ArrayList<NodeVariable> variables;
    private ArrayList<FunctionEvaluator> functions;
    double costo;
    int passi = -1;
    long inizio, fine;
    int mink;
    // massimo numero di passi
    private int kMax = 10000;
    // parametri per il simulated annealing
    private double T = 1000;
    private double costValue = Double.NaN;
    private double haltValue = Double.NaN;
    private boolean pleaseReport = false;
    private String reportpath = "";
    private String report = "";
    private boolean stepbystep = false;
    private boolean updateOnlyAtEnd = true;
    final static int debug = test.DebugVerbosity.debugEris;
    // 0 -> inf
    // 1 -> relaxed
    private int type = 0;
    private HashMap<NodeVariable, Integer> assegnamenti = new HashMap<NodeVariable, Integer>();

    public Eris(String op, COP_Instance cop) throws ParameterNotFoundException {

        double changeInfinityTo = 0;
        double infinity;

        if (op.equalsIgnoreCase("max")) {
            this.op = "max";
            this.costValue = Double.NEGATIVE_INFINITY;
            this.haltValue = Double.POSITIVE_INFINITY;
            infinity = Double.NEGATIVE_INFINITY;
            changeInfinityTo = -1e05;
        } else if (op.equalsIgnoreCase("min")) {
            this.op = "min";
            this.costValue = Double.POSITIVE_INFINITY;
            this.haltValue = Double.NEGATIVE_INFINITY;
            infinity = Double.POSITIVE_INFINITY;
            changeInfinityTo = 5000;//1e05;
        } else {
            throw new ParameterNotFoundException("Unknown operation: " + op);
        }

        this.cop = cop;


        //this.variables = new ArrayList<NodeVariable>(this.cop.getNodevariables());
        this.variables = new ArrayList<NodeVariable>();
        for (NodeVariable nv : this.cop.getNodevariables()) {
            if (nv.size() > 1) {
                this.variables.add(nv);
            } else if (nv.size() == 1) {
                nv.setStateIndex(0);
            }
        }

        this.functions = new ArrayList<FunctionEvaluator>();
        FunctionEvaluator oldFe, newFe;
        for (NodeFunction oldNf : this.cop.getNodefunctions()) {
            oldFe = oldNf.getFunction();
            newFe = oldFe.getClone();
            if (newFe.changeValueToValue(infinity, changeInfinityTo)) {
                functions.add(newFe);
            } else {
                functions.add(oldFe);
            }
        }
    }

    public Eris(String op, COP_Instance cop, String type) throws ParameterNotFoundException {

        if (type.equalsIgnoreCase("noinf")) {
            this.type = 1;
        }
        double changeInfinityTo = 0;
        double infinity;

        if (op.equalsIgnoreCase("max")) {
            this.op = "max";
            this.costValue = Double.NEGATIVE_INFINITY;
            this.haltValue = Double.POSITIVE_INFINITY;
            infinity = Double.NEGATIVE_INFINITY;
            changeInfinityTo = -1e05;
        } else if (op.equalsIgnoreCase("min")) {
            this.op = "min";
            this.costValue = Double.POSITIVE_INFINITY;
            this.haltValue = Double.NEGATIVE_INFINITY;
            infinity = Double.POSITIVE_INFINITY;
            changeInfinityTo = 5000;//1e05;
        } else {
            throw new ParameterNotFoundException("Unknown operation: " + op);
        }

        this.cop = cop;


        //this.variables = new ArrayList<NodeVariable>(this.cop.getNodevariables());
        this.variables = new ArrayList<NodeVariable>();
        for (NodeVariable nv : this.cop.getNodevariables()) {
            if (nv.size() > 1) {
                this.variables.add(nv);
            } else if (nv.size() == 1) {
                nv.setStateIndex(0);
            }
        }

        this.functions = new ArrayList<FunctionEvaluator>();
        FunctionEvaluator oldFe, newFe;
        if (this.type == 0) {
            for (NodeFunction oldNf : this.cop.getNodefunctions()) {
                oldFe = oldNf.getFunction();
                newFe = oldFe.getClone();
                if (newFe.changeValueToValue(infinity, changeInfinityTo)) {
                    functions.add(newFe);
                } else {
                    functions.add(oldFe);
                }
            }
            // no inf
        } else if (this.type == 1) {
            for (NodeFunction oldNf : this.cop.getNodefunctions()) {
                oldFe = oldNf.getFunction();
                functions.add(oldFe);
            }
        }
    }

    public void pleaseReport(String file) {

        this.pleaseReport = true;
        this.reportpath = file;

    }

    public void randomInit() {
        for (NodeVariable x : this.variables) {
            try {
                x.setRandomValidValue();
            } catch (NoMoreValuesException ex) {
                // do nothing
            }
        }
        try {
            //this.costo = this.cop.actualValue();
            this.costo = this.getCosto();
        } catch (VariableNotSetException ex) {
            ex.printStackTrace();
        }
        this.passi = -1;
    }

    private NodeVariable randomNodo() {

        Random rnd = new Random();

        return this.variables.get(rnd.nextInt(this.variables.size()));
    }

    private double update(double temperatura, int k) {
        return (this.T * Math.exp(-k / (8 * this.T)));
    }

    public void solve() {


        if (this.op.equalsIgnoreCase("max")) {
            this.costValue = Double.NEGATIVE_INFINITY;
        } else if (this.op.equalsIgnoreCase("min")) {
            this.costValue = Double.POSITIVE_INFINITY;
        }

        // inizializza random
        this.randomInit();
        this.inizio = System.currentTimeMillis();
        this.report = "";
        // costo
        double costoPrecedente = Double.NaN;
        double relaxedCostoPrecedente = Double.NaN;
        double relaxedCosto = Double.NaN;
        try {
            costoPrecedente = this.getCosto();
            relaxedCostoPrecedente = ((Relaxable_MS_COP_Instance)cop).actualRelaxedValue();
        } catch (VariableNotSetException ex) {
            ex.printStackTrace();
        }
        int k = 0;
        mink = 0;
        NodeVariable nodo = null;
        boolean ripeti = false;
        int valorePrecedente = -1;
        double delta = 0;

        double probab = -1;
        double temperatura = 0;
        if (this.T >= 0) {
            temperatura = this.T;
        } else {
            boolean repeat = true;
            do {
                try {
                    temperatura = this.stimaT(1000, 0.8);
                    repeat = false;
                } catch (VariableNotSetException ex) {
                    this.randomInit();
                    repeat = true;
                }
            } while (repeat);
        }
        if (debug >= 2) {
            String dmethod = Thread.currentThread().getStackTrace()[2].getMethodName();
            String dclass = Thread.currentThread().getStackTrace()[2].getClassName();
            System.out.println("---------------------------------------");
            System.out.println("[class: " + dclass + " method: " + dmethod + "] " + "Temperatura iniziale = " + temperatura);
            System.out.println("---------------------------------------");
        }
        Random rnd = new Random();
        String status;
        boolean ffFound = false;
        this.report += "max_iterations_number=" + this.kMax + "\n";





        try {
            //while (temperatura > this.TLow) {
            while (k <= this.kMax) {

                do {
                    try {
                        ripeti = false;
                        nodo = this.randomNodo();

                        valorePrecedente = nodo.getStateIndex();


                        nodo.setAnotherRandomValidValue();


                    } catch (NoMoreValuesException ex) {
                        //} catch (Exception ex) {
                        ripeti = true;
                    }
                } while (ripeti);
                if (debug >= 3) {
                    String dmethod = Thread.currentThread().getStackTrace()[2].getMethodName();
                    String dclass = Thread.currentThread().getStackTrace()[2].getClassName();
                    System.out.println("---------------------------------------");
                    System.out.println("[class: " + dclass + " method: " + dmethod + "] " + "nodo scelto: " + nodo);
                    System.out.println("---------------------------------------");
                }
                try {
                    //this.costo = this.cop.actualValue();
                    this.costo = this.getCosto();
                    relaxedCosto = ((Relaxable_MS_COP_Instance)cop).actualRelaxedValue();
                } catch (VariableNotSetException ex) {
                    ex.printStackTrace();
                }


                if (this.costo == costoPrecedente) {
                    if (Double.isInfinite(this.costo)) {
                        delta = relaxedCosto - relaxedCostoPrecedente;
                        probab = Math.exp(-delta / temperatura);
                        
                        if (rnd.nextDouble() < probab) {
                            // aggiorno
                            // aggiorno
                            costoPrecedente = this.costo;
                            relaxedCostoPrecedente = relaxedCosto;
                            // aggiorno il minimo
                            // non devo aggiornare
                            if (debug >= 3) {
                                String dmethod = Thread.currentThread().getStackTrace()[2].getMethodName();
                                String dclass = Thread.currentThread().getStackTrace()[2].getClassName();
                                System.out.println("---------------------------------------");
                                System.out.println("[class: " + dclass + " method: " + dmethod + "] " + "Costs INFINITE: aggiorno " + this.costo);
                                System.out.println("---------------------------------------");
                            }
                        } else {
                            // non aggiorno
                            nodo.setStateIndex(valorePrecedente);
                            this.costo = costoPrecedente;
                            relaxedCosto = relaxedCostoPrecedente;
                            if (debug >= 3) {
                                String dmethod = Thread.currentThread().getStackTrace()[2].getMethodName();
                                String dclass = Thread.currentThread().getStackTrace()[2].getClassName();
                                System.out.println("---------------------------------------");
                                System.out.println("[class: " + dclass + " method: " + dmethod + "] " + "Costs INFINITE: non aggiorno " + this.costo);
                                System.out.println("---------------------------------------");
                            }
                        }

                    } else {
                        if (debug >= 3) {
                            String dmethod = Thread.currentThread().getStackTrace()[2].getMethodName();
                            String dclass = Thread.currentThread().getStackTrace()[2].getClassName();
                            System.out.println("---------------------------------------");
                            System.out.println("[class: " + dclass + " method: " + dmethod + "] " + "Costs EQUAL not infinite: non aggiorno " + this.costo);
                            System.out.println("---------------------------------------");
                        }
                        // non aggiorno
                        nodo.setStateIndex(valorePrecedente);
                        this.costo = costoPrecedente;
                        relaxedCosto = relaxedCostoPrecedente;
                    }
                } else {

                    if (this.op.equalsIgnoreCase("max")) {
                        //delta = costoPrecedente - this.costo;
                        // TODO: implement max!
                        throw new Exception("Please implement maximization");
                    } else if (this.op.equalsIgnoreCase("min")) {
                        //delta = this.costo - costoPrecedente;
                        if (this.costo < costoPrecedente) {
                            if (debug >= 2) {
                                String dmethod = Thread.currentThread().getStackTrace()[2].getMethodName();
                                String dclass = Thread.currentThread().getStackTrace()[2].getClassName();
                                System.out.println("---------------------------------------");
                                System.out.println("[class: " + dclass + " method: " + dmethod + "] " + "Cost < Old: aggiorno " + this.costo);
                                System.out.println("---------------------------------------");
                            }
                            // aggiorna
                            costoPrecedente = this.costo;
                            relaxedCostoPrecedente = relaxedCosto;
                            // aggiorno solo se..
                            if (this.costValue > this.costo) {
                                this.costValue = this.costo;
                                salvaAssegnamento();
                                mink = k;
                            }
                        } else {
                            // cost > oldCost
                            if (Double.isInfinite(this.costo)) {
                                delta = relaxedCosto - relaxedCostoPrecedente;
                                probab = Math.exp(-delta / temperatura);

                                if (rnd.nextDouble() < probab) {
                                    if (debug >= 2) {
                                        String dmethod = Thread.currentThread().getStackTrace()[2].getMethodName();
                                        String dclass = Thread.currentThread().getStackTrace()[2].getClassName();
                                        System.out.println("---------------------------------------");
                                        System.out.println("[class: " + dclass + " method: " + dmethod + "] " + "Costs different, cost infinite: aggiorno " + this.costo);
                                        System.out.println("---------------------------------------");
                                    }
                                    // aggiorno
                                    // aggiorno
                                    costoPrecedente = this.costo;
                                    relaxedCostoPrecedente = relaxedCosto;
                                    // aggiorno il minimo
                                    // non devo aggiornare
                                } else {
                                    if (debug >= 3) {
                                        String dmethod = Thread.currentThread().getStackTrace()[2].getMethodName();
                                        String dclass = Thread.currentThread().getStackTrace()[2].getClassName();
                                        System.out.println("---------------------------------------");
                                        System.out.println("[class: " + dclass + " method: " + dmethod + "] " + "Costs different, cost infinite: non aggiorno " + this.costo);
                                        System.out.println("---------------------------------------");
                                    }
                                    // non aggiorno
                                    nodo.setStateIndex(valorePrecedente);
                                    this.costo = costoPrecedente;
                                    relaxedCosto = relaxedCostoPrecedente;
                                }
                            } else {
                                // new cost is not an infinite, still greater than oldCost
                                delta = this.costo - costoPrecedente;
                                probab = Math.exp(-delta / temperatura);
                                //probab = 0.5;
                                if (rnd.nextDouble() < probab) {
                                    if (debug >= 2) {
                                        String dmethod = Thread.currentThread().getStackTrace()[2].getMethodName();
                                        String dclass = Thread.currentThread().getStackTrace()[2].getClassName();
                                        System.out.println("---------------------------------------");
                                        System.out.println("[class: " + dclass + " method: " + dmethod + "] " + "Costs different, cost finite: aggiorno " + this.costo);
                                        System.out.println("---------------------------------------");
                                    }
                                    // aggiorno
                                    // aggiorno
                                    costoPrecedente = this.costo;
                                    relaxedCostoPrecedente = relaxedCosto;
                                    // aggiorno il minimo
                                    // non devo aggiornare
                                } else {
                                    if (debug >= 3) {
                                        String dmethod = Thread.currentThread().getStackTrace()[2].getMethodName();
                                        String dclass = Thread.currentThread().getStackTrace()[2].getClassName();
                                        System.out.println("---------------------------------------");
                                        System.out.println("[class: " + dclass + " method: " + dmethod + "] " + "Costs different, cost finite: non aggiorno " + this.costo);
                                        System.out.println("---------------------------------------");
                                    }
                                    // non aggiorno
                                    nodo.setStateIndex(valorePrecedente);
                                    this.costo = costoPrecedente;
                                    relaxedCosto = relaxedCostoPrecedente;
                                }
                            }
                        }
                    }


                }


                if (checkValue(this.costo)) {
                    throw new ResultOkException();
                }





                // è ora di aggiornare
                k++;
                temperatura = update(temperatura, k);

                status = "iteration_" + k + "=" + this.cop.status();


                if (!this.updateOnlyAtEnd) {
                    if (this.pleaseReport) {
                        this.report += status + "\n";
                    } else {
                        System.out.println(status);
                    }
                }

                if (this.stepbystep) {
                    System.out.print("Iteration " + (k) + "/" + this.kMax + " completed, press enter to continue");
                    try {
                        System.in.read();
                    } catch (IOException ex) {
                        //skip
                    }
                    System.out.println("");
                }

            }
        } catch (ResultOkException rex) {
            ffFound = true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.fine = System.currentTimeMillis();
        //ripristino assegnamento OK
        this.ripristinaAssegnamento();
        this.passi = k - 1;


        status = "final=" + this.costValue;//.cop.status();


        if (!this.updateOnlyAtEnd) {
            if (this.pleaseReport) {
            } else {
                System.out.println(status);
            }
        }

        this.report += status + "\n";
        this.report += "total time [ms]=" + (fine - inizio) + "\n";

        this.report += "latest value got at iteration=" + mink + "\n";
        this.report += "total number of iteration=" + passi + "\n";
        this.report += "fixed point found=";
        if (ffFound) {
            this.report += "Y";
        } else {
            this.report += "N";
        }
        this.report += "\n";
        if (this.pleaseReport) {
            try {
                Utils.stringToFile(this.report, this.reportpath);
            } catch (IOException ex) {
                System.out.println("Sorry but I'm unable to write the report to the file " + this.reportpath);
            }
        } else {
            System.out.println(report);
        }


    }

    private boolean checkValue(double value) {
        if (this.op.equalsIgnoreCase("max")) {
            return value >= this.haltValue;
        } else if (this.op.equalsIgnoreCase("min")) {
            return value <= this.haltValue;
        } else {
            //nothing to do!
            return false;
        }
    }

    public void setIterationsNumber(int n) {
        this.kMax = n;
    }

    public void setStepbystep(boolean stepbystep) {
        this.stepbystep = stepbystep;
    }

    public void setUpdateOnlyAtEnd(boolean updateOnlyAtEnd) {
        this.updateOnlyAtEnd = updateOnlyAtEnd;
    }

    private double getCosto() throws VariableNotSetException {
        // FIXME! only for sum!
        /*double cost = 0;
        RelaxableFunctionEvaluator rfe;
        for (FunctionEvaluator fe : this.functions) {
            if (this.type==0){
                cost += fe.actualValue();
            } else if (this.type==1){
                rfe = (RelaxableFunctionEvaluator) fe;
                cost += rfe.relaxedActualValue();
            }
        }
        return cost;*/
        if (this.type==0){
            return this.cop.actualValue();
        } else if (this.type==1){
            //return ((Relaxable_MS_COP_Instance)cop).actualRelaxedValue();
            return this.cop.actualValue();
        }
        else {
            return this.cop.actualValue();
        }
    }

    public void setTemperature(double T) {
        this.T = T;
    }

    public void temperaturaStimata() {
        this.T = -1;
    }

    private double stimaT(int numCicli, double prob) throws VariableNotSetException {
        int count = 0;
        double deltaSum = 0;
        for (int i = 0; i < numCicli; i++) {

            if (debug >= 3) {
                System.out.println("Ciclo di stima numero: " + i);
            }

            NodeVariable nodo = null;
            boolean ripeti = false;
            int valorePrecedente = -1;
            double delta = 0;
            double costoIniziale = 0;
            do {
                try {
                    ripeti = false;
                    nodo = this.randomNodo();
                    valorePrecedente = nodo.getStateIndex();
                    nodo.setAnotherRandomValidValue();
                } catch (NoMoreValuesException ex) {
                    //} catch (Exception ex) {
                    ripeti = true;
                }
            } while (ripeti);

            if (this.op.equalsIgnoreCase("max")) {
                delta = this.getCosto() - costoIniziale;
                if (delta > 0) {
                    if (debug >= 3) {
                        System.out.println("delta vale: " + delta);
                    }
                    count++;
                    deltaSum += delta;
                }
            } else if (this.op.equalsIgnoreCase("min")) {
                delta = costoIniziale - this.getCosto();
                if (delta < 0) {
                    if (debug >= 3) {
                        System.out.println("delta vale: " + delta);
                    }
                    count++;
                    deltaSum += -1.0 * (delta);
                }
            }

            nodo.setStateIndex(valorePrecedente);


        }

        return (double) (-(deltaSum / (double) count) / Math.log(prob));

    }

    public int minK() {
        return this.mink;
    }

    public void setType(int type) {
        this.type = type;
    }

    private void salvaAssegnamento() {
        for (NodeVariable x : this.variables){
            this.assegnamenti.put(x, x.getStateIndex());
        }
    }

    private void ripristinaAssegnamento(){
        for (Entry<NodeVariable, Integer> entry : this.assegnamenti.entrySet()){
            entry.getKey().setStateIndex(entry.getValue());
        }
    }
}