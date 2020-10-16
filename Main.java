
import javax.print.attribute.standard.Destination;
import javax.sound.midi.SysexMessage;
import java.io.*;
import java.util.*;

import static java.lang.Integer.parseInt;
import static java.lang.Integer.rotateLeft;

public class Main {
    static int fail=0;
    static int issuewidth;
    static int counte=0;
    static int rename_count=0;
    static int decode_count = 0;
    static int cycleCount = 0;
    static int register_size;
    //Datatypes Used:
    static ArrayList<Integer> fetchCycle = new ArrayList<>();
    static ArrayList<Integer> decodeCycle = new ArrayList<>();
    static ArrayList<Integer> renameCycle = new ArrayList<>();
    static ArrayList<Integer> dispatchCycle = new ArrayList<>();
    static ArrayList<Integer> issueCycle = new ArrayList<>();
    static ArrayList<Integer> wbCycle = new ArrayList<>();
    static ArrayList<Integer> commitCycle = new ArrayList<>();
    static ArrayList<String> instruction = new ArrayList<>();
    static ArrayList<String> inst_fetched = new ArrayList<>();
    static ArrayList<String> inst_decoded = new ArrayList<>();
    static ArrayList<String> inst_renamed = new ArrayList<>();
    static ArrayList<String> inst_dispatched = new ArrayList<>();
    static ArrayList<String> inst_issued = new ArrayList<>();
    static ArrayList<String> inst_wb = new ArrayList<>();
    static ArrayList<String> inst_commit = new ArrayList<>();
    static HashMap<Integer, Integer> mapTable = new HashMap<>();
    static ArrayList<Integer> freeList = new ArrayList<>();
    static ArrayList<String> overWritten = new ArrayList<>();
    static HashMap<String, ArrayList<String>> dependency = new HashMap<>();
    static ArrayList<String> destReg = new ArrayList<>();
    static HashMap<String, String> wbComplete = new HashMap();
    static HashMap<Integer, Integer> issueQueue = new HashMap<>();
    static HashMap<Integer, Integer> wbQueue = new HashMap<>();
    static HashMap<Integer, Integer> commitQueue = new HashMap<>();
    static HashMap<String,Integer> instOrder = new HashMap<>();
    static HashMap<String,Integer>swOrder = new HashMap<>();
    static ArrayList<ArrayList<Integer>>Output = new ArrayList<>();
    static ArrayList<String>reject = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        instruction = inRead(args[0]);
        String specs = null;
        specs = instruction.get(0);
        instruction.remove(0);
        String[] size = specs.split(",");
        issuewidth = parseInt(size[1]);
        register_size = parseInt(size[0]);
        if(register_size<32){
            fail=1;
        }
        for (int i = 0; i < 32; i++) {
            mapTable.put(i, i);
        }
        for (int k = 32; k <register_size; k++) {
            freeList.add(k);
        }
        int fetchIndex = 0;
        int committedInst=0;
        for(int q=0;q<instruction.size();q++){
            issueCycle.add(-1);
            wbCycle.add(-1);
            commitCycle.add(-1);
        }
        while (committedInst<instruction.size()) {
            if(fail==1){
                break;
            }
            committedInst=Commit(committedInst);
            Wb();
            Issue();
            Dispatch();
            Rename();
            Decode();
            fetchIndex = fetch(fetchIndex);
            cycleCount++;
        }
        emitOutput();
    }

    public static ArrayList<String> inRead(String a) throws IOException {
        String line;
        ArrayList<String> instruction = new ArrayList<String>();
        BufferedReader br = null;
        FileReader fr = null;
        try {
            fr = new FileReader(a);
            br = new BufferedReader(fr);
        } catch (IOException e) {
            e.printStackTrace();
        }
        while ((line = br.readLine()) != null) {
            instruction.add(line);
        }
        br.close();
        if (fr != null) {
            fr.close();
        }
        return instruction;
    }
    public static int fetch(int fetchIndex) {
        for (int i = fetchIndex; i < fetchIndex + issuewidth; i++) {
            if (i >= instruction.size()) {
                break;
            }

            fetchCycle.add(cycleCount);
            inst_fetched.add(instruction.get(i));
        }
        fetchIndex += issuewidth;
        return fetchIndex;
    }
    public static void Decode() {
        while (inst_fetched.size() != 0) {
            decodeCycle.add(cycleCount);
            inst_decoded.add(inst_fetched.get(0));
            inst_fetched.remove(0);
            if (inst_fetched.size() == 0) {
                break;
            }
        }
    }
    public static void Rename() {
        String a1 = null;
        String b1 = null;
        String c1 = null;
        for(int i=rename_count;i<inst_decoded.size();i++) {
            String[] reg = inst_decoded.get(i).split(",");
            if((freeList.size()!=0)&&(reg[0].charAt(0)!='S')){
                if (reg[0].charAt(0) == 'L') {
                    b1 = reg[2];
                    overWritten.add(Integer.toString(mapTable.get(parseInt(reg[1]))));
                    a1 = Integer.toString(freeList.get(0));
                    mapTable.replace(parseInt(reg[1]), freeList.get(0));
                    freeList.remove(0);
                    c1 = Integer.toString(mapTable.get(parseInt(reg[3])));
                    inst_decoded.set(i, reg[0] + "," + a1 + "," + b1 + "," + c1);
                }
                else if (reg[0].charAt(0) == 'R') {
                    overWritten.add(Integer.toString(mapTable.get(parseInt(reg[1]))));
                    a1 = Integer.toString(freeList.get(0));
                    b1 = Integer.toString(mapTable.get(parseInt(reg[2])));
                    c1 = Integer.toString(mapTable.get(parseInt(reg[3])));
                    mapTable.replace(parseInt(reg[1]), freeList.get(0));
                    freeList.remove(0);
                    inst_decoded.set(i, reg[0] + "," + a1 + "," + b1 + "," + c1);
                }
                else if (reg[0].charAt(0) == 'I') {
                    c1 = reg[3];
                    overWritten.add(Integer.toString(mapTable.get(parseInt(reg[1]))));
                    a1 = Integer.toString(freeList.get(0));
                    mapTable.replace(parseInt(reg[1]), freeList.get(0));
                    freeList.remove(0);
                    b1 = Integer.toString(mapTable.get(parseInt(reg[2])));
                    inst_decoded.set(i, reg[0] + "," + a1 + "," + b1 + "," + c1);
                }
                renameCycle.add(cycleCount);
                inst_renamed.add(inst_decoded.get(i));
                rename_count++;
                String[] reg1 = inst_decoded.get(i).split(",");
                if(reg1[0].charAt(0)!='S'){
                    if(wbComplete.containsKey(reg1[1])){
                        String b=wbComplete.get(reg1[1]);
                        int c=instOrder.get(b);
                        int d=commitCycle.get(c);
                        if(d!=-1){
                            reject.add(b);
                            wbComplete.remove(reg1[1]);
                        }
                    }
                }
            }
            else if (reg[0].charAt(0) == 'S') {
                overWritten.add("X");
                a1 = Integer.toString(mapTable.get(parseInt(reg[1])));
                b1 = reg[2];
                c1 = Integer.toString(mapTable.get(parseInt(reg[3])));
                inst_decoded.set(i, reg[0] + "," + a1 + "," + b1 + "," + c1);
                renameCycle.add(cycleCount);
                inst_renamed.add(inst_decoded.get(i));
                rename_count++;
            }
            else{
                break;
            }
        }
    }
    public static void Dispatch() {
        while (inst_renamed.size() != 0) {
            dispatchCycle.add(cycleCount);
            inst_dispatched.add(inst_renamed.get(0));
            inst_renamed.remove(0);
            if (inst_renamed.size() == 0) {
                break;
            }
        }
        for (int i = 0; i < issuewidth; i++) {
            if (i >= inst_dispatched.size()) {
                break;
            }
            if (inst_dispatched.size() == 0) {
                break;
            }
            String[] reg = inst_dispatched.get(i).split(",");
            if (reg[0].charAt(0) != 'S') {
                if(!destReg.contains(reg[1])){
                    destReg.add(reg[1]);
                }
                else{
                    destReg.remove(reg[1]);
                    destReg.add(reg[1]);
                }
            }
        }
        for (int j = 0; j < issuewidth; j++) {
            if (j >= inst_dispatched.size()) {
                break;
            }
            if (inst_dispatched.size() == 0) {
                break;
            }
            String[] rg = inst_dispatched.get(j).split(",");
            if (rg[0].charAt(0) == 'L') {
                if (destReg.contains(rg[3])) {
                    ArrayList<String> test = new ArrayList<>();
                    test.add(rg[3]);
                    dependency.put(inst_dispatched.get(j), test);
                }
            } else if (rg[0].charAt(0) == 'S') {
                if (destReg.contains(rg[3]) | destReg.contains(rg[1])) {
                    ArrayList<String> test = new ArrayList<>();
                    if (destReg.contains(rg[3])) {
                        test.add(rg[3]);
                    }
                    if (destReg.contains(rg[1])) {
                        test.add(rg[1]);
                    }
                    dependency.put(inst_dispatched.get(j), test);
                }
            } else if (rg[0].charAt(0) == 'R') {
                if (destReg.contains(rg[2]) | destReg.contains(rg[3])) {
                    ArrayList<String> test = new ArrayList<>();
                    if (destReg.contains(rg[3])) {
                        test.add(rg[3]);
                    }
                    if (destReg.contains(rg[2])) {
                        test.add(rg[2]);
                    }
                    dependency.put(inst_dispatched.get(j), test);
                }
            } else if (rg[0].charAt(0) == 'I') {
                if (destReg.contains(rg[2])) {
                    ArrayList<String> test = new ArrayList<>();
                    test.add(rg[2]);
                    dependency.put(inst_dispatched.get(j), test);
                }
            }
        }
    }
    public static void Issue() {
        while (inst_dispatched.size() != 0) {
            inst_issued.add(inst_dispatched.get(0));
            inst_dispatched.remove(0);
            if (inst_dispatched.size() == 0) {
                break;
            }
        }
        int i = 0;
        int j = 0;
        while (i < inst_issued.size()) {
            String[]conservative=inst_issued.get(i).split(",");
            if(!instOrder.containsKey(inst_issued.get(i))){
                instOrder.put(inst_issued.get(i),i);
                if(conservative[0].charAt(0)=='S'){
                    swOrder.put(inst_issued.get(i),i);
                }
            }
            if (!dependency.containsKey(inst_issued.get(i))) {
                if (issueCycle.get(i) == -1) {
                    int d = issue_set(cycleCount);
                    issueCycle.set(i, d);
                }
            } else if (dependency.containsKey(inst_issued.get(i))) {
                if (issueCycle.get(i) == -1) {
                    ArrayList<String> dep = new ArrayList<>();
                    dep = dependency.get(inst_issued.get(i));
                    int length = dep.size();
                    int max = 0;
                    int counter = 0;
                    for (int h = 0; h < length; h++) {
                        if (wbComplete.containsKey(dep.get(h))) {
                            counter++;
                        }
                    }
                    if (counter == length) {
                        for (int g = 0; g < length; g++) {
                            String a = dep.get(g);
                            String ac = wbComplete.get(a);
                            int ab = inst_wb.indexOf(ac);
                            if (wbCycle.get(ab) > max) {
                                max = wbCycle.get(ab);
                            }
                        }
                        if (cycleCount >= max) {
                            int d = cycleCount;
                            int e = issue_set(d);
                            if (swOrder.size() != 0) {
                                int c = Collections.max(swOrder.values());
                                int f = issueCycle.get(c);
                                if ((f >= e) && (conservative[0].charAt(0) == 'L')) {
                                    int val = issueQueue.get(d);
                                    val -= 1;
                                    issueQueue.replace(d, val);
                                    int g = issue_set(f + 1);
                                    issueCycle.set(i, g);
                                } else {
                                    issueCycle.set(i, e);
                                }
                            }
                            else{
                                issueCycle.set(i, e);
                            }
                        }
                    }
                }
            }
            i++;
        }
    }
    public static void Wb(){
        for(int i=0;i<issueCycle.size();i++){
            if(issueCycle.get(i)!=-1) {
                String[] wbcons = inst_issued.get(i).split(",");
                if (!inst_wb.contains(inst_issued.get(i))) {
                    inst_wb.add(inst_issued.get(i));
                    int d = wb_set(cycleCount);
                    int s = instOrder.get(inst_issued.get(i));
                    if (swOrder.size() != 0) {
                        int c = Collections.max(swOrder.values());
                        int f = wbCycle.get(c);
                        if ((f >= d) && (wbcons[0].charAt(0) == 'L')) {
                            int val = wbQueue.get(f);
                            val -= 1;
                            wbQueue.replace(f, val);
                            int g = wb_set(f + 1);
                            wbCycle.set(s, g);
                        } else {
                            wbCycle.set(s, d);
                        }
                    }
                    else{
                        wbCycle.set(s, d);
                    }
                }
            }
        }
        for(int j=0;j<inst_wb.size();j++){
            String[]reg=inst_wb.get(j).split(",");
            if(reg[0].charAt(0)!='S'){
                if(!reject.contains(inst_wb.get(j))) {
                    wbComplete.put(reg[1], inst_wb.get(j));
                }
            }
        }
    }
    public static int Commit(int a){
        int count=0;
        for(int i=0;i<inst_wb.size();i++) {
            int e = instOrder.get(inst_wb.get(i));
            String[]commitcons=inst_wb.get(i).split(",");
            if (wbCycle.get(e) != -1) {
                if (!inst_commit.contains(inst_wb.get(i))) {
                    inst_commit.add(inst_wb.get(i));
                    count++;
                    int d = commit_set(cycleCount);
                    int c=Collections.max(swOrder.values());
                    int f=commitCycle.get(c);
                    if((f>=d)&&(commitcons[0].charAt(0)=='L')){
                        int val=commitQueue.get(f);
                        val-=1;
                        commitQueue.replace(f,val);
                        int g=commit_set(f+1);
                        commitCycle.set(e,g);
                    }
                    else {
                        commitCycle.set(e, d);
                    }
                }
            }

        }
        int min=commitCycle.get(0);
        for(int i=1;i<commitCycle.size();i++){
            if(commitCycle.get(i)!=-1) {
                if (commitCycle.get(i) >= min) {
                    min = commitCycle.get(i);
                } else if (commitCycle.get(i) < min) {
                    int f = commit_set(min);
                    commitCycle.set(i, f);
                    min = f;
                }
            }
        }
        for(int g=counte;g<commitCycle.size();g++){
            if(g>=overWritten.size()){
                break;
            }
            if(g>=inst_issued.size()){
                break;
            }
            String inst=inst_issued.get(g);
            int index=instOrder.get(inst);
            if(cycleCount>commitCycle.get(index)){
                if(commitCycle.get(index)!=-1){
                    if(overWritten.get(g)!="X") {
                        freeList.add(parseInt(overWritten.get(g)));
                    }
                    counte++;
                }
            }
        }
        a+=count;
        return a;
    }
    public static int issue_set(int c) {
        if (issueQueue.containsKey(c)) {
            int e = issueQueue.get(c);
            if (e >= issuewidth) {
                issue_set(c + 1);
            } else {
                issueQueue.replace(c, e + 1);
            }
        } else {
            issueQueue.put(c, 1);
        }
        return c;
    }
    public static int wb_set(int c) {
        if (wbQueue.containsKey(c)) {
            int e = wbQueue.get(c);
            if (e >= issuewidth) {
                wb_set(c + 1);
            } else {
                wbQueue.replace(c, e + 1);
            }
        } else {
            wbQueue.put(c, 1);
        }
        return c;
    }
    public static int commit_set(int c){
        if (commitQueue.containsKey(c)) {
            int e = commitQueue.get(c);
            if (e >= issuewidth) {
                wb_set(c + 1);
            } else {
                commitQueue.replace(c, e + 1);
            }
        } else {
            commitQueue.put(c, 1);
        }
        return c;
    }
    public static void emitOutput()throws IOException{
        PrintWriter writer = new PrintWriter("out.txt");
        for(int i=0;i<instruction.size();i++) {
            if (fail == 0) {
                writer.println(fetchCycle.get(i) + "," + decodeCycle.get(i) + "," + renameCycle.get(i) + "," + dispatchCycle.get(i) + "," + issueCycle.get(i) + "," + wbCycle.get(i) + "," + commitCycle.get(i));
            }
        }
        writer.close();
        }
}


