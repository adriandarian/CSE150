package nachos.threads;

import java.util.LinkedList;
import java.util.Iterator;

import nachos.ag.BoatGrader;
import nachos.machine.*;

public class Boat {
  static BoatGrader bg;
  static final int Oahu = 0;
  static final int Molokai = 1;
  static int boatLocation = Oahu;
  static int cntPassengers = 0;
  static Lock boatLock = new Lock();
  static Condition2 waitingOnOahu = new Condition2(boatLock);
  static Condition2 waitingOnMolokai = new Condition2(boatLock);
  static Condition2 waitingOnBoatFull = new Condition2(boatLock);
  static int OahuChildren = 0;
  static int OahuAdults = 0;
  static int MolokaiChildren = 0;
  static int MolokaiAdults = 0;
  static Communicator reporter = new Communicator();

  public static void selfTest() {
  }

  public static void begin(int adults, int children, BoatGrader b) {
    bg = b;
    OahuChildren = children;
    OahuAdults = adults;
    MolokaiChildren = 0;
    MolokaiAdults = 0;

    Runnable r_child = new Runnable() {
      public void run() {
        int location = Oahu;
        ChildItinerary(location);
      };
    };

    Runnable r_adult = new Runnable() {
      public void run() {
        int location = Oahu;
        AdultItinerary(location);
      };
    };

    for (int i = 0; i < children; i++) {
      KThread t = new KThread(r_child);
      t.setName("Boat Thread - Child - #" + (i + 1));
      t.fork();
    }

    for (int i = 0; i < adults; i++) {
      KThread t = new KThread(r_adult);
      t.setName("Boat Thread - Adult - #" + (i + 1));
      t.fork();
    }

    while (true) {
      int recv = reporter.listen();
      System.out.println("***** Receive " + recv);
      if (recv == children + adults) {
        break;
      }
    }
  }

  static void ChildItinerary(int location) {
    System.out.println("***** ChildItinerary, place: " + location);
    boatLock.acquire();
    while (true) {
      if (location == 12345678) {
        Lib.assertTrue(false);
        break;
      }

      if (location == Oahu) {
        while (boatLocation != Oahu || cntPassengers >= 2 || (OahuAdults > 0 && OahuChildren == 1)) {
          waitingOnOahu.sleep();
        }

        waitingOnOahu.wakeAll();
        if (OahuAdults == 0 && OahuChildren == 1) {
          OahuChildren--;
          bg.ChildRowToMolokai();
          boatLocation = Molokai;
          location = Molokai;
          MolokaiChildren++;
          cntPassengers = 0;
          reporter.speak(MolokaiChildren + MolokaiAdults);
          waitingOnMolokai.sleep();
        } else if (OahuChildren > 1) {
          cntPassengers++;
          if (cntPassengers == 2) {
            waitingOnBoatFull.wake();
            waitingOnBoatFull.sleep();
            OahuChildren--;
            bg.ChildRideToMolokai();
            cntPassengers = cntPassengers - 2;
            boatLocation = Molokai;
            location = Molokai;
            MolokaiChildren++;
            reporter.speak(MolokaiChildren + MolokaiAdults);
            waitingOnMolokai.wakeAll();
            waitingOnMolokai.sleep();
          } else if (cntPassengers == 1) {
            waitingOnBoatFull.sleep();
            OahuChildren--;
            bg.ChildRowToMolokai();
            location = Molokai;
            MolokaiChildren++;
            waitingOnBoatFull.wake();
            waitingOnMolokai.sleep();
          }
        }
      } else if (location == Molokai) {
        Lib.assertTrue(MolokaiChildren > 0);
        while (boatLocation != Molokai) {
          waitingOnMolokai.sleep();
        }
        MolokaiChildren--;
        bg.ChildRowToOahu();
        boatLocation = Oahu;
        location = Oahu;
        OahuChildren++;
        waitingOnOahu.wakeAll();
        waitingOnOahu.sleep();
      }
    }
    boatLock.release();
  }

  static void AdultItinerary(int location) {
    boatLock.acquire(); 
    while (true) {
      if (location == Oahu) {
        while (cntPassengers > 0 || OahuChildren > 1 || boatLocation != Oahu) {
          waitingOnOahu.sleep();
        }
        bg.AdultRowToMolokai();
        OahuAdults--;
        boatLocation = Molokai;
        MolokaiAdults++;
        location = Molokai; 
        reporter.speak(MolokaiChildren+MolokaiAdults);
        Lib.assertTrue(MolokaiChildren > 0);
        waitingOnMolokai.wakeAll();
        waitingOnMolokai.sleep();
      }
      else if (location == Molokai) {
        waitingOnMolokai.sleep();
      }
      else {
        Lib.assertTrue(false);
        break; 
      }
      boatLock.release(); 
    }
  }

  static void SampleItinerary() {
    System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
    bg.AdultRowToMolokai();
    bg.ChildRideToMolokai();
    bg.AdultRideToMolokai();
    bg.ChildRideToMolokai();
  }

}
