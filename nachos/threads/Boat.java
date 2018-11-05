package nachos.threads;

import nachos.ag.BoatGrader;

public class Boat {
  static BoatGrader bg;
  static Communicator communicator;

  static Lock incrLock, boatLock, catmLock, cawcLock, wwLock;
  static int adultsOahu, childrenOahu, adultsMolokai, childrenMolokai;
  static boolean passengerTaken;
  static Condition childrenAllToMolokai, comebackAndWakeCoordinator, waitWake;

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
    incrLock = new Lock();
    boatLock = new Lock();
    catmLock = new Lock();
    childrenAllToMolokai = new Condition(catmLock);
    cawcLock = new Lock();
    comebackAndWakeCoordinator = new Condition(cawcLock);
    wwLock = new Lock();
    waitWake = new Condition(wwLock);
    adultsOahu = 0;
    adultsMolokai = 0;
    childrenOahu = 0;
    childrenMolokai = 0;
    passengerTaken = false;

    // Create threads here. See section 3.4 of the Nachos for Java
    // Walkthrough linked from the projects page.

    for (int i = 0; i < adults; i++) {
      KThread t = new KThread(new Runnable() {
        @Override
        public void run() {
          AdultItinerary();
        }
      });
      t.setName("Adult #" + i);
      t.fork(); 
    }

    for (int i = 0; i < children; i++) {
      KThread t = new KThread(new Runnable() {
        @Override
        public void run() {
          ChildItinerary();
        }
      });
      t.setName("Child #" + i);
      t.fork();
    }

    communicator.listen();
    //bg.AllCrossed();

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
    b.initializeAdult();

    incrLock.acquire();
    adultsOahu++;
    incrLock.release();

    catmLock.acquire();
    childrenAllToMolokai.sleep();
    catmLock.release();
    bg.AdultRowToMolokai();
    adultsOahu--;
    cawcLock.acquire();
    comebackAndWakeCoordinator.wake();
    cawcLock.release();
  }

  static void ChildItinerary() {
    b.initializeChild();

    incrLock.acquire();
    childrenOahu++;
    incrLock.release();

    ThreadedKernel.alarm.waitUntil(500);

    while(true) {
      boatLock.acquire();
      if (!passengerTaken && childrenOahu > 0) {
        passengerTaken = true;
        bg.ChildRideToMolokai();
        childrenOahu--;
        childrenMolokai++;
        boatLock.release();

        while(true) {
          cawcLock.acquire();
          comebackAndWakeCoordinator.sleep();
          cawcLock.release();
          bg.ChildRowToOahu();
          wwLock.acquire();
          waitWake.wake();
          wwLock.release();
          bg.ChildRideToMolokai();
        }
      } else {
        passengerTaken = false;
        bg.ChildRowToMolokai();
        childrenOahu--;
        childrenMolokai++;
        if (childrenOahu > 0) {
          bg.ChildRowToOahu();
          childrenMolokai--;
          childrenOahu++;
          boatLock.release();
        } else {
          bg.ChildRowToOahu();
          childrenMolokai--;
          childrenOahu++;
          while(adultsOahu > 0) {
            catmLock.acquire();
            childrenAllToMolokai.wake();
            catmLock.release();
            wwLock.acquire();
            waitWake.wake();
            wwLock.release();
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
