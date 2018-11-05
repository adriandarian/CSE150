package nachos.threads;

import nachos.ag.BoatGrader;

public class Boat {
  static BoatGrader bg;
  static Communicator communicator;

  static Lock incr, boat, catm, cawc, ww;
  static int adultsOahu, childrenOahu, adultsMolokai, childrenMolokai;
  static boolean passenger;
  static Condition cToMolokai, coordinator, wake;

  public static void selfTest() {
    BoatGrader b = new BoatGrader();

    System.out.println("\n ***Testing Boats with only 2 children***");
    begin(0, 2, b);

    // System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
    // begin(1, 2, b);

    // System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
    // begin(3, 3, b);
  }

  public static void begin(int adults, int children, BoatGrader b) {
    // Store the externally generated autograder in a class
    // variable to be accessible by children.
    bg = b;

    // Instantiate global variables here
    communicator = new Communicator();
    incr = new Lock();
    boat = new Lock();
    catm = new Lock();
    cToMolokai = new Condition(catm);
    cawc = new Lock();
    coordinator = new Condition(cawc);
    ww = new Lock();
    wake = new Condition(ww);
    adultsOahu = 0;
    adultsMolokai = 0;
    childrenOahu = 0;
    childrenMolokai = 0;
    passenger = false;

    // Create threads here. See section 3.4 of the Nachos for Java
    // Walkthrough linked from the projects page.

    for (int m = 0; m < adults; m++) {
      KThread n = new KThread(new Runnable() {
        @Override
        public void run() {
          AdultItinerary();
        }
      });
      n.setName("Adult #" + m);
      n.fork(); 
    }

    for (int o = 0; o < children; o++) {
      KThread p = new KThread(new Runnable() {
        @Override
        public void run() {
          ChildItinerary();
        }
      });
      p.setName("Child #" + o);
      p.fork();
    }

    communicator.listen();
    // bg.AllCrossed();

    /*Runnable r = new Runnable() {
      public void run() {
        SampleItinerary();
      }
    };
    KThread t = new KThread(r);
    t.setName("Sample Boat Thread");
    t.fork();*/

  }

  static void AdultItinerary() {
    /*
     * This is where you should put your solutions. Make calls to the BoatGrader to
     * show that it is synchronized. For example: bg.AdultRowToMolokai(); indicates
     * that an adult has rowed the boat across to Molokai
     */
    // b.initializeAdult();

    incr.acquire();
    adultsOahu++;
    incr.release();

    catm.acquire();
    cToMolokai.sleep();
    catm.release();
    bg.AdultRowToMolokai();
    adultsOahu--;
    cawc.acquire();
    coordinator.wake();
    cawc.release();
  }

  static void ChildItinerary() {
    // b.initializeChild();

    incr.acquire();
    childrenOahu++;
    incr.release();

    ThreadedKernel.alarm.waitUntil(500);

    while(true) {
      boat.acquire();
      if (!passenger && childrenOahu > 0) {
        passenger = true;
        bg.ChildRideToMolokai();
        childrenOahu--;
        childrenMolokai++;
        boat.release();

        while(true) {
          cawc.acquire();
          coordinator.sleep();
          cawc.release();
          bg.ChildRowToOahu();
          ww.acquire();
          wake.wake();
          ww.release();
          bg.ChildRideToMolokai();
        }
      } else {
        passenger = false;
        bg.ChildRowToMolokai();
        childrenOahu--;
        childrenMolokai++;
        if (childrenOahu > 0) {
          bg.ChildRowToOahu();
          childrenMolokai--;
          childrenOahu++;
          boat.release();
        } else {
          bg.ChildRowToOahu();
          childrenMolokai--;
          childrenOahu++;
          while(adultsOahu > 0) {
            catm.acquire();
            cToMolokai.wake();
            catm.release();
            ww.acquire();
            wake.wake();
            ww.release();
            bg.ChildRowToMolokai();
            bg.ChildRowToOahu();
          }
          bg.ChildRowToMolokai();
          childrenMolokai++;
          childrenOahu--;
          communicator.speak(1);
          return ;
        }
      }
    }
  }

  static void SampleItinerary() {
    // Please note that this isn't a valid solution (you can't fit
    // all of them on the boat). Please also note that you may not
    // have a single thread calculate a solution and then just play
    // it back at the autograder -- you will be caught.
    System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
    bg.AdultRowToMolokai();
    bg.ChildRideToMolokai();
    bg.AdultRideToMolokai();
    bg.ChildRideToMolokai();
  }

}
