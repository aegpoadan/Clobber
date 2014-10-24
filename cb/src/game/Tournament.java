package game;
import java.net.*;
import java.io.*;
import java.lang.Exception;
import java.util.*;

import ttt.*;
import connect4.*;
import breakthrough.*;
import clobber.*;
import game.GameState.Status;
import hijara.*;
import hex.*;
import connect6.*;

public class Tournament {
	public enum GameType { TTT, Connect4, Breakthrough, Clobber, Hijara, Hex, Connect6 };
	public static GameType game = GameType.Clobber;

	public static Params tournamentParams;
    public static Params gameParams;
    
	public static Client [] clients;
    public static ArrayList<GameThread> threads;
	public static final char SEP = File.separatorChar;
	
	private static class Game {
		public int h, a;
		public int gameNum;
		public Game(int h, int a, int n)
		{
			this.h = h;
			this.a = a;
			this.gameNum = n;
		}
	}
	
    private static String printableName(String name)
    {
    	int maxLen = gameParams.bool("TIES") ? 13 : 9;
    	if (name.length() <= maxLen)
    		return name;
    	else
    		return name.substring(0, maxLen);
    }
    private static int randInt(int lo, int hi)
    {
    	int delta = hi - lo + 1;
    	return (int)(Math.random() * delta) + lo;
    }
    private static void displaySummary(int wins, int losses, int ties)
    {
    	if (gameParams.bool("TIES")) {
    		System.out.printf("(%3d %3d %3d) ", wins, losses, ties);
    	} else {
    		System.out.printf("(%3d %3d) ", wins, losses);
    	}
    }
    /*
    private static String format(int max, int a, int b, int c)
    {
    	int digs = (int)Math.ceil(Math.log10(max));
    	if (digs <= 0) {
    		digs = 1;
    	}
    	String sfmt = String.format("%c%dd", '%', digs);
    	String fmt = sfmt + " " + sfmt + " " + sfmt;
    	String s = String.format(fmt, a, b, c);
    	return s;
    }
    */
    private static void shuffle(ArrayList<Game> list)
    {
    	int sz = list.size();
    	for (int i=0; i<sz; i++) {
    		int newSpot = randInt(i, sz-1);
    		
    		Game old = list.get(i);
    		Game neww = list.get(newSpot);
    		list.set(i, neww);
    		list.set(newSpot, old);
    	}
    }
    public static boolean randomTeam(int t)
    { return t < 0 || !clients[t].deterministic; }
    public static boolean stochasticGame(int h, int a)
    { return randomTeam(h) || randomTeam(a); }
    public static boolean deterministicGame(int h, int a)
    { return !stochasticGame(h, a); }
    public static void schedule(ArrayList<Game> sched, int p1, int p2)
    {
    	int NUM_GAMES = tournamentParams.integer("NUMGAMES");
    	int numToPlay;
    	if (stochasticGame(p1, p2)) {
    		numToPlay = NUM_GAMES;
    	} else {
    		numToPlay = 1;
    	}
        for (int i=0; i<numToPlay; i++) {
        	sched.add(new Game(p1, p2, i));
        }
    }
    public static char lastChar(String s)
    {
    	int len = s.length();
    	return s.charAt(len-1);
    }
    public static boolean xor(boolean a, boolean b)
    { return (a && !b) || (!a && b); }
    public static void displaySummary(String suffixes, String format, int NUM_CLIENTS,
    								int [][][] headToHeadSummary, int [][] homeSummary)
    {
    	boolean doAll = suffixes.length() == 0;
		System.out.printf(format, "");
        for (int p1=0; p1<NUM_CLIENTS; p1++) {
        	String name = clients[p1].name;
        	if (doAll || suffixes.indexOf(lastChar(name)) != -1) {
        		System.out.printf(format, printableName(name));
        	}
        }
        System.out.println();
    
        for (int p1=0; p1<NUM_CLIENTS; p1++) {
        	String name = clients[p1].name;
        	if (doAll || suffixes.indexOf(lastChar(name)) != -1) {
        		System.out.printf(format, printableName(clients[p1].name));
                for (int p2=0; p2<NUM_CLIENTS; p2++) {
                	String name2 = clients[p2].name;
                	if (doAll || suffixes.indexOf(lastChar(name2)) != -1) {
    	            	if (p1 == p2) {
    	            		System.out.printf(format, "");
    	            	} else {
    	            		displaySummary(headToHeadSummary[p1][p2][0], headToHeadSummary[p1][p2][1], headToHeadSummary[p1][p2][2]);
    	            	}
                	}
                }
            	displaySummary(homeSummary[p1][0], homeSummary[p1][1], homeSummary[p1][2]);
            	System.out.printf("%n");
        	}
        }
    	
    }
    public static void launchTournament(GameState st, GameMove move)
    {
    	boolean systematicTournament = false;
    	int systematicPlayers = 3;
    	
    	File dumpsDir = new File("Dumps");
    	dumpsDir.mkdir();
    	Date date = new Date();
		String dumpDir = "Dumps" + SEP + date.toString() + SEP;
		dumpDir = dumpDir.replace(":", ";");
    	File file = new File(dumpDir);
    	file.mkdir();
    	GameThread.dir = dumpDir;
    	int NUM_CLIENTS = tournamentParams.integer("NUMCLIENTS");
    	int PORT = tournamentParams.integer("PORT");
    	int NUM_GAMES = tournamentParams.integer("NUMGAMES");

    	int INIT_LIMIT = gameParams.integer("INITTIME");
    	int MOVE_LIMIT = gameParams.integer("MOVETIME");
    	int GAME_LIMIT = gameParams.integer("GAMETIME");
    	int FINAL_PLAY_LIMIT = gameParams.integer("MAXMOVETIME");
    	int MAX_WARNINGS = gameParams.integer("NUMWARNINGS");
    	
    	int p1, p2;
    	int i;
        int headToHeadSummary[][][] = new int [NUM_CLIENTS][NUM_CLIENTS][3];
        int homeSummary[][] = new int [NUM_CLIENTS][3];
        int awaySummary[][] = new int [NUM_CLIENTS][3];
        int totalSummary[][] = new int [NUM_CLIENTS][3];
		ArrayList<Game> schedule = new ArrayList<Game>();
		threads = new ArrayList<GameThread>();
		String format = gameParams.bool("TIES") ? "%13s " : "%9s ";
		
		try {
			ServerSocket socket = new ServerSocket(PORT);
	        clients = new Client [NUM_CLIENTS];
			for (i=0; i<NUM_CLIENTS; i++) {
				clients[i] = new Client(socket, INIT_LIMIT, MOVE_LIMIT, FINAL_PLAY_LIMIT,
											GAME_LIMIT, MAX_WARNINGS);
				if (clients[i].name.toUpperCase().contains("HUMAN")) {
					clients[i].gameTimeLimit = clients[i].moveLimit = 
						clients[i].finalPlayLimit = gameParams.integer("HUMANTIME");  
				}
		    	File subdir = new File(dumpDir + SEP + clients[i].name);
		    	subdir.mkdir();
				System.out.printf("%s has joined%n", clients[i].name);
			}

			if (systematicTournament) {
				for (p1=0; p1<NUM_CLIENTS; p1++) {
		            for (p2=1; p2<=systematicPlayers; p2++) {
	            		schedule(schedule, p1, -p2);
	            		schedule(schedule, -p2, p1);
		            }
				}
			} else {
				for (p1=0; p1<NUM_CLIENTS; p1++) {
		            for (p2=0; p2<NUM_CLIENTS; p2++) {
		            	if (p1 == p2) continue;
	            		schedule(schedule, p1, p2);
		            }
				}
			}
			shuffle(schedule);

			int remain = schedule.size();
	
			while (remain > 0) {
				Thread.sleep(500);
				boolean cont = true;
				while (cont) {
					cont = false;
					for (i=0; i<schedule.size(); i++) {
						Game g = schedule.get(i);
						p1 = g.h;
						p2 = g.a;
						int actualPlayer = p1 >= 0 ? p1 : p2;
						int consecWins = Tournament.tournamentParams.integer("CONSECWINS");
						if (systematicTournament) {
							if (!clients[actualPlayer].busy) {
								GameThread game;
								if (g.h >= 0) {
									game = new GameThread(clients[g.h], g.h, null, g.a, g.gameNum, move.clone(), st.clone());
								} else {
									game = new GameThread(null, g.h, clients[g.a], g.a, g.gameNum, move.clone(), st.clone());
								}
								game.start();
								clients[actualPlayer].busy = true;
								threads.add(game);
								schedule.remove(i);
								System.out.printf("%s (%d)%n", clients[actualPlayer].name, remain);
								cont = true;
							}
						} else if ((headToHeadSummary[p1][p2][0] >= consecWins &&
								headToHeadSummary[p1][p2][1] + headToHeadSummary[p1][p2][2] == 0) ||
							(headToHeadSummary[p1][p2][0] + headToHeadSummary[p1][p2][2] == 0 && 
								headToHeadSummary[p1][p2][1] >= consecWins)) {
							schedule.remove(i);
							remain--;
							if (headToHeadSummary[p1][p2][0] == 0) {
								headToHeadSummary[p1][p2][1]++; 
								homeSummary[p1][1]++;
								awaySummary[p2][0]++;
								totalSummary[p1][1]++;
								totalSummary[p2][0]++;
							} else {
								headToHeadSummary[p1][p2][0]++; 
								homeSummary[p1][0]++;
								awaySummary[p2][1]++;
								totalSummary[p1][0]++;
								totalSummary[p2][1]++;
							}
						} else if (!clients[g.h].busy && !clients[g.a].busy) {
							GameThread game = new GameThread(clients[g.h], g.h, clients[g.a], g.a,
													g.gameNum, move.clone(), st.clone());
							game.start();
							clients[g.h].busy = clients[g.a].busy = true;
							threads.add(game);
							schedule.remove(i);
							System.out.printf("%s-%s (%d)%n", clients[g.h].name, clients[g.a].name, remain);
							cont = true;
						}
					}
				}
				i = 0;
				while (i<threads.size()) {
					GameThread t = threads.get(i);
					if (t.isAlive()) {
						i++;
					} else {
						boolean systematic = t.homeID < 0 || t.awayID < 0;
						boolean homeIsSystematic = systematic && t.homeID < 0;
						boolean awayIsSystematic = systematic && t.awayID < 0;
						
						p1 = t.homeID;
						p2 = t.awayID;
						if (!homeIsSystematic) clients[p1].busy = false;
						if (!awayIsSystematic) clients[p2].busy = false;
	                    GameState.Status outcome = t.result;
						threads.remove(i);
						remain--;
						
						int cnt = stochasticGame(p1, p2) ? 1 : NUM_GAMES;
	                    if (outcome == GameState.Status.HOME_WIN) {
	                    	System.out.printf("home (%s) won%n", homeIsSystematic ? "SYS" : clients[p1].name);
	                    	if (!systematic) headToHeadSummary[p1][p2][0] += cnt;
							if (!awayIsSystematic) awaySummary[p2][1] += cnt;
							if (!homeIsSystematic) homeSummary[p1][0] += cnt;
							if (!homeIsSystematic) totalSummary[p1][0] += cnt;
							if (!awayIsSystematic) totalSummary[p2][1] += cnt;
	                    } else if (outcome == GameState.Status.AWAY_WIN) {
	                    	System.out.printf("away (%s) won%n", awayIsSystematic ? "SYS" : clients[p2].name);
	                    	if (!systematic) headToHeadSummary[p1][p2][1] += cnt;
	                    	if (!awayIsSystematic) awaySummary[p2][0] += cnt;
	                    	if (!homeIsSystematic) homeSummary[p1][1] += cnt;
	                    	if (!homeIsSystematic) totalSummary[p1][1] += cnt;
							if (!awayIsSystematic) totalSummary[p2][0] += cnt;
	                    } else if (outcome == GameState.Status.DRAW) {
	                    	System.out.println(" draw\n");
	                    	if (!systematic) headToHeadSummary[p1][p2][2] += cnt;
	                    	if (!awayIsSystematic) awaySummary[p2][2] += cnt;
	                    	if (!homeIsSystematic) homeSummary[p1][2] += cnt;
	                    	if (!homeIsSystematic) totalSummary[p1][2] += cnt;
							if (!awayIsSystematic) totalSummary[p2][2] += cnt;
	                    } else {
	                    	System.err.println("Error with game outcome");
	                    }
					}
				}
			}
		
			for (int j=0; j<NUM_CLIENTS; j++) {
				clients[j].simpleMsg("DONE");
			}
			
			displaySummary("", format, NUM_CLIENTS, headToHeadSummary, homeSummary);

	        System.out.printf("%n%n");
	        for (p1=0; p1<NUM_CLIENTS; p1++) {
	        	System.out.printf(format + "%3s ", printableName(clients[p1].name),
	        					clients[p1].DQd ? "DQd" : "  ");
	        	displaySummary(totalSummary[p1][0], totalSummary[p1][1], totalSummary[p1][2]);
	        	System.out.println();
	        }
	        System.out.println();
		}
		catch (Exception e) {
			System.out.println("Server problem" + e);
		}
	}

	public static void main(String[] args)
	{
		tournamentParams = new Params("config" + SEP + "tournament.txt");
		System.out.printf("Starting %s tournament%n", game.toString());
		System.out.printf("%d clients%n", tournamentParams.integer("NUMCLIENTS"));
		System.out.printf("%d clients%n", tournamentParams.integer("NUMCLIENTS2"));
		
		if (game == GameType.TTT) {
			gameParams = new Params("config" + SEP + "ttt.txt");
			Tournament.launchTournament(new TTTState(), new TTTMove(0, 0));
		} else if (game == GameType.Connect4) {
			gameParams = new Params("config" + SEP + "connect4.txt");
			Tournament.launchTournament(new Connect4State(),	new Connect4Move(0));
		} else if (game == GameType.Breakthrough) {
			gameParams = new Params("config" + SEP + "Breakthrough.txt");
			Tournament.launchTournament(new BreakthroughState(), new BreakthroughMove());
		} else if (game == GameType.Clobber) {
			gameParams = new Params("config" + SEP + "Clobber.txt");
			Tournament.launchTournament(new ClobberState(), new ClobberMove());
		} else if (game == GameType.Hijara) {
			gameParams = new Params("config" + SEP + "Hijara.txt");
			Tournament.launchTournament(new HijaraState(), new HijaraMove());
		} else if (game == GameType.Hex) {
			gameParams = new Params("config" + SEP + "Hex.txt");
			Tournament.launchTournament(new HexState(), new HexMove());
		} else if (game == GameType.Connect6) {
			gameParams = new Params("config" + SEP + "Connect6.txt");
			Tournament.launchTournament(new Connect6State(), new Connect6Move());
		}
		System.out.println("Tournament is over");
	}
}
