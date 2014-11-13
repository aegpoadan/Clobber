package clobber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import game.*;

public class ABMODDNETTSTMTSubmitVersion extends GamePlayer {
	
	public static final int MAX_SCORE = 10000;
	public static final int DEPTH_BASE = 8;
	public static final int TIME_BASE = 20;
	public static final int TIME_FACOTR = 2;
	public static final int TIME_CONSTANT = 5;
	public static final int MINIMUM_REMAINING_TIME = 3;
	public static final int NUM_OF_THREADS = 4;
	public static final int KEYRANGE = 0x7FFFFFFF;
	public static final int SCORETABLE_SIZE = 0xFFFFF;
	public static final int TRANSTABLE_SIZE = 0xFFFFF;
	public static final int WHEN_TO_USE_TRANSTABLE = 6;

	private int CurBoardKey;
	private int DepthLimit;
	private double TimeLeft;
	private int[][][] KeyTable;
	private ScoredClobberMove BestMove;
	private ScoredClobberMove LastBestMove;
	ExecutorService Executor;
	private boolean UseTransTable;
	
	private class AlphaBetaThread implements Callable<ScoredClobberMove> {

		private int ThreadID;
		private ClobberState thisState;
		private HashMap<Integer, ScoreTableEntry> ScoreTable;
		private HashMap<Integer, TransTableEntry> TransTable;
		private ScoredClobberMove[] MvStack;
		
		// Performance evaluation use
		private int maxDepthReached;
		private int transTableHits;
		private int scoreTableHits;
		private int transTableMisses;
		private int scoreTableMisses;
		
		public AlphaBetaThread(ClobberState st, int id) {
			ThreadID = id;
			thisState = st;
			int maxMoves = ClobberState.ROWS*ClobberState.COLS*2 
					 - ClobberState.ROWS-ClobberState.COLS;
			MvStack = new ScoredClobberMove[maxMoves];
			for (int i=0; i<maxMoves; i++) 
				MvStack[i] = new ScoredClobberMove();
		
			// Better way?
			ScoreTable = new HashMap<Integer, ScoreTableEntry>(SCORETABLE_SIZE);
			if (UseTransTable)
				TransTable = new HashMap<Integer, TransTableEntry>(TRANSTABLE_SIZE);
		}
		

		private char hasPawn(char[][] board, int r, int c) {
			if (Util.inrange(r, ClobberState.ROWS-1) && Util.inrange(c, ClobberState.COLS-1)) {
				return board[r][c];
			}
			return ' ';
		}
		
		private boolean hasBlockNum(int[][] whichBlock, int r, int c) {
			if (Util.inrange(r, ClobberState.ROWS-1) && Util.inrange(c, ClobberState.COLS-1)) {
				if (whichBlock[r][c] != 0)
					return true;
			}
			return false;
		}
		
		private void checkAdj(int r, int c, char who, Block block, char[][] board) {
			// whatever the adjacent pawn is, we increment the score.
			// also check diagonal allies and enemies to see
			// if we have backups or enemy have backups
			
			char pieceLeft = hasPawn(board, r, c-1);
			char pieceRight = hasPawn(board, r, c+1);
			char pieceUp = hasPawn(board, r-1, c);
			char pieceDown = hasPawn(board, r+1, c);
			char upperLeft = hasPawn(board,r-1,c-1);
			char upperRight = hasPawn(board,r-1,c+1);
			char lowerLeft = hasPawn(board,r+1,c-1);
			char lowerRight = hasPawn(board,r+1,c+1);
			char upperUpper = hasPawn(board,r-2,c);
			char lowerLower = hasPawn(board,r+2,c);
			char leftLeft = hasPawn(board,r,c-2);
			char rightRight = hasPawn(board,r,c+2);
			
			boolean shouldCheckBackup = false;
			boolean hasBackup = false;
			boolean[] enemyHasBackup = new boolean[4];
			
			if (who == ClobberState.homeSym) {
				if (pieceLeft == ClobberState.homeSym) {
					block.homeScore++;
					hasBackup = true;
				} else if (pieceLeft == ClobberState.awaySym) {
					shouldCheckBackup = true;
					block.homeScore++;
					if (upperLeft == ClobberState.awaySym
						|| lowerLeft == ClobberState.awaySym
						|| leftLeft == ClobberState.awaySym){
						enemyHasBackup[0] = true;
					}
				}
				if (pieceRight == ClobberState.homeSym) {
					block.homeScore++;
					hasBackup = true;
				} else if (pieceRight == ClobberState.awaySym) { 
					shouldCheckBackup = true;
					block.homeScore++;
					if (upperRight == ClobberState.awaySym
						|| lowerRight == ClobberState.awaySym
						|| rightRight == ClobberState.awaySym){
						enemyHasBackup[1] = true;
					}
				}
				if (pieceUp == ClobberState.homeSym) {
					hasBackup = true;
					block.homeScore++;
				} else if (pieceUp == ClobberState.awaySym) { 
					shouldCheckBackup = true;
					block.homeScore++;
					if (upperLeft == ClobberState.awaySym
						|| upperRight == ClobberState.awaySym
						|| upperUpper == ClobberState.awaySym){
						enemyHasBackup[2] = true;
					}
				}
				if (pieceDown == ClobberState.homeSym) {
					hasBackup = true;
					block.homeScore++;
				} else if (pieceDown == ClobberState.awaySym) {
					shouldCheckBackup = true;
					block.homeScore++;
					if (lowerLeft == ClobberState.awaySym
						|| lowerRight == ClobberState.awaySym
						|| lowerLower == ClobberState.awaySym){
						enemyHasBackup[3] = true;
					}
				}
				if (shouldCheckBackup && hasBackup) {
					block.homeScore++;
				} else if (shouldCheckBackup && !hasBackup) {
					for (int i = 0; i < 4; i++) {
						if (enemyHasBackup[i]) {
							block.homeScore--;
							break;
						}
					}
				}
			} else {
				if (pieceLeft == ClobberState.awaySym) {
					hasBackup = true;
					block.awayScore++;
				} else if (pieceLeft == ClobberState.homeSym) {
					shouldCheckBackup = true;
					block.awayScore++;
					if (upperLeft == ClobberState.homeSym
						|| lowerLeft == ClobberState.homeSym
						|| leftLeft == ClobberState.homeSym){
						enemyHasBackup[0] = true;
					}
				}
				if (pieceRight == ClobberState.awaySym) {
					hasBackup = true;
					block.awayScore++;
				} else if (pieceRight == ClobberState.homeSym) { 
					shouldCheckBackup = true;
					block.awayScore++;
					if (upperRight == ClobberState.homeSym
						|| lowerRight == ClobberState.homeSym
						|| rightRight == ClobberState.homeSym){
						enemyHasBackup[1] = true;
					}
				}
				if (pieceUp == ClobberState.awaySym) {
					hasBackup = true;
					block.awayScore++;
				} else if (pieceUp == ClobberState.homeSym) { 
					shouldCheckBackup = true;
					block.awayScore++;
					if (upperLeft == ClobberState.homeSym
						|| upperRight == ClobberState.homeSym
						|| upperUpper == ClobberState.homeSym){
						enemyHasBackup[2] = true;
					}
				}
				if (pieceDown == ClobberState.awaySym) {
					hasBackup = true;
					block.awayScore++;
				} else if (pieceDown == ClobberState.homeSym) { 
					shouldCheckBackup = true;
					block.awayScore++;
					if (lowerLeft == ClobberState.homeSym
						|| lowerRight == ClobberState.homeSym
						|| lowerLower == ClobberState.homeSym){
						enemyHasBackup[3] = true;
					}
				}
				if (shouldCheckBackup && hasBackup) {
					block.awayScore++;
				}  else if (shouldCheckBackup && !hasBackup) {
					for (int i = 0; i < 4; i++) {
						if (enemyHasBackup[i]) {
							block.awayScore--;
							break;
						}
					}
				}
			}
		}
		
		private double evalBoard(char[][] board) {
			int blockAssigned = 0;
			double score = 0;
			Block[] blocks = new Block[ClobberState.ROWS*ClobberState.COLS/2 + 1];
			int[][] blockBoard = new int[ClobberState.ROWS][ClobberState.COLS];
			
			for (int r = 0; r < ClobberState.ROWS; r++) {
				for (int c = 0; c < ClobberState.COLS; c++) {
					char whichPos = board[r][c];
					if (whichPos != ClobberState.emptySym) {
						int blockNum = 0;
						if (hasBlockNum(blockBoard, r-1, c))
							blockNum = blockBoard[r-1][c];
						else if (hasBlockNum(blockBoard, r+1, c))
							blockNum = blockBoard[r+1][c];
						else if (hasBlockNum(blockBoard, r, c-1))
							blockNum = blockBoard[r][c-1];
						else if (hasBlockNum(blockBoard, r, c+1))
							blockNum = blockBoard[r][c+1];
						if (blockNum == 0) {
							blockAssigned++;
							blockNum = blockAssigned;
							blocks[blockNum] = new Block();
						}
						blockBoard[r][c] = blockNum;
						
						if (whichPos == ClobberState.homeSym)
							blocks[blockNum].homeCount++;
						else
							blocks[blockNum].awayCount++;
						checkAdj(r, c , whichPos, blocks[blockNum], board);
					}
				}
			}
			
			for (int i = 1; i <= blockAssigned; i++) {
				Block b = blocks[i];
				if (b.homeCount == 0 || b.awayCount == 0)
					continue;
				if (b.homeCount >= b.awayCount) {
					score += ((double)b.homeCount)/((double)b.awayCount)
							 *(b.homeScore-b.awayScore);
				} else {
					score += ((double)b.awayCount)/((double)b.homeCount)
							 *(b.homeScore-b.awayScore);
				}
			}
			return score;
		}
		
		private boolean terminalValue(GameState brd, ScoredClobberMove mv)
		{
			GameState.Status status = brd.getStatus();	
			if (status == GameState.Status.HOME_WIN) {
				mv.set(MAX_SCORE);
			} else if (status == GameState.Status.AWAY_WIN) {
				mv.set(-MAX_SCORE);
			} else {
				return false;
			}
			return true;
		}

		private void reOrder(ScoredClobberMove[] mvArray, int count,
				boolean isToMax) {
			if (isToMax)
				Arrays.sort(mvArray, 0, count - 1, Collections.reverseOrder());
			else
				Arrays.sort(mvArray, 0, count - 1);
		}
		
		private void undoMove (ClobberState board, ScoredClobberMove mv) {
			board.board[mv.row1][mv.col1] = board.board[mv.row2][mv.col2];
			board.board[mv.row2][mv.col2] 
					= board.board[mv.row2][mv.col2] == ClobberState.homeSym
					  ? ClobberState.awaySym : ClobberState.homeSym;
			board.numMoves--;
			board.status = GameState.Status.GAME_ON;
			board.togglePlayer();
		}
		
		private int addValidMove (ClobberState board, ScoredClobberMove mv, 
								   ScoredClobberMove[] mvArray, int index,
								   int boardKey) {
			if (board.moveOK(mv)) {
				int newBoardKey = computeBoardKey(boardKey, mv, board.getWho());
				int scoreTableKey = newBoardKey%SCORETABLE_SIZE;
				board.makeMove(mv);
				if (!terminalValue(board, mv)) {
					double score = queryUpdateScoreTable(newBoardKey,
								   scoreTableKey, board.board);
					mv.set(score);
				}
				undoMove(board, mv);
				mvArray[index] = mv;
				return index+1;
			}
			return index;
		}
		
		private int createMoveArray(ClobberState board, ScoredClobberMove[] moveArray,
									int boardKey) {
			int moveCount = 0;
			for (int r = 0; r < ClobberState.ROWS; r++) {
				for (int c = 0; c < ClobberState.COLS; c++) {
					ScoredClobberMove moveUp = new ScoredClobberMove(r,c,r+1,c);
					ScoredClobberMove moveDown = new ScoredClobberMove(r,c,r-1,c);
					ScoredClobberMove moveLeft = new ScoredClobberMove(r,c,r,c-1);
					ScoredClobberMove moveRight = new ScoredClobberMove(r,c,r,c+1);
					moveCount=addValidMove(board,moveUp,moveArray,moveCount,boardKey);
					moveCount=addValidMove(board,moveDown,moveArray,moveCount,boardKey);
					moveCount=addValidMove(board,moveLeft,moveArray,moveCount,boardKey);
					moveCount=addValidMove(board,moveRight,moveArray,moveCount,boardKey);
				}
			}
			return moveCount;
		}
		
		private void updateTransTable (int boardKey, int transTableKey, double alpha, 
				double beta, int currDepth, double score, 
				HashMap<Integer, TransTableEntry> transTable) {
			TransTableEntry e = new TransTableEntry(boardKey, currDepth, score);
			if (score <= alpha) {
				e.type = TranspositionType.LOWERBOUND;
			} else if (score >= beta) {
				e.type = TranspositionType.UPPERBOUND;
			} else {
				e.type = TranspositionType.EXACT_VALUE;
			}
			transTable.put(transTableKey, e);
		}
		
		private double queryUpdateScoreTable(int boardKey, int scoreTableKey, 
											 char[][] board) {
			double score = 0;
			ScoreTableEntry ste = ScoreTable.get(scoreTableKey);
			if (ste != null && ste.boardKey == boardKey) {
				scoreTableHits++;
				score = ste.score;
			} else {
				scoreTableMisses++;
				score = evalBoard(board);
				ScoreTableEntry nste = new ScoreTableEntry(boardKey, score);
				ScoreTable.put(scoreTableKey, nste);
			}
			return score;
		}
		
		private void alphaBeta(ClobberState board, int currDepth, 
				int boardKey, double alpha, double beta) {
			if (Thread.currentThread().isInterrupted())
				return;
			int transTableKey = boardKey % TRANSTABLE_SIZE;
			if (UseTransTable) {
				boolean isHit = false;
				TransTableEntry tte = TransTable.get(transTableKey);
				if (tte != null && tte.boardKey == boardKey
						&& tte.depth <= currDepth) {
					if (tte.type == TranspositionType.EXACT_VALUE) {
						MvStack[currDepth].score = tte.score;
						transTableHits++;
						return;
					}
					if (tte.type == TranspositionType.LOWERBOUND
							&& tte.score > alpha) {
						alpha = tte.score;
						transTableHits++;
						isHit = true;
					} else if (tte.type == TranspositionType.UPPERBOUND
							&& tte.score < beta) {
						beta = tte.score;
						transTableHits++;
						isHit = true;
					}
					if (alpha >= beta) {
						transTableHits++;
						MvStack[currDepth].score = tte.score;
						return;
					}
				}
				if (!isHit)
					transTableMisses++;
			}
			
			boolean toMaximize = (board.getWho() == GameState.Who.HOME);
			boolean isTerminal = terminalValue(board, MvStack[currDepth]);
			int scoreTableKey = boardKey%SCORETABLE_SIZE;
			
			if (isTerminal || currDepth == DepthLimit) {
				double score = 0;
				if (isTerminal) {
					score = MvStack[currDepth].score;
				} else {
					score = queryUpdateScoreTable(boardKey, 
							   scoreTableKey, board.board);
					MvStack[currDepth].set(score);
				}
				if (UseTransTable)
					updateTransTable(boardKey, transTableKey, alpha, beta, 
						 currDepth, score, TransTable);
			} else {
				ScoredClobberMove nextMove = MvStack[currDepth+1];
				ScoredClobberMove bestMove = MvStack[currDepth];
				double bestScore = (toMaximize ? 
						Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
				bestMove.set(bestScore);
				
				// Create a move array with one level down forward checking
				ScoredClobberMove[] mvArray = new ScoredClobberMove
						[ClobberState.ROWS*ClobberState.COLS*2-board.numMoves*2];
				int moveCount = createMoveArray(board, mvArray, boardKey);
				reOrder(mvArray, moveCount, toMaximize);

				// Performance evaluation use
				if (currDepth == 0)
					System.out.println("Total moves possible: " + moveCount);
				maxDepthReached = Math.max(currDepth, maxDepthReached);
						
				for (int i = 0; i < moveCount; i++) {
					
					// Work assigned for this thread
					if (currDepth == 0) {
						if (ThreadID == 0 &&
							Math.abs(LastBestMove.score) == Double.POSITIVE_INFINITY)
							LastBestMove = mvArray[0];
						i = NUM_OF_THREADS*i + ThreadID;
						if (i >= moveCount) {
							System.out.println("Thread ID: " + ThreadID
									+ " Thread moves possible: " 
									+ (i - ThreadID)/NUM_OF_THREADS);
							break;
						}
					}
					ScoredClobberMove mv = mvArray[i];
					int newBoardKey = computeBoardKey(boardKey, mv, board.getWho());
					board.makeMove(mv);

					alphaBeta(board, currDepth + 1, newBoardKey, alpha, beta);
					undoMove(board, mv);

					// Check out the results, update Max/Min nodes
					if (toMaximize && nextMove.score > bestMove.score)
						bestMove.set(mv, nextMove.score);
					else if (!toMaximize && nextMove.score < bestMove.score)
						bestMove.set(mv, nextMove.score);

					// Update alpha and beta. Perform pruning, if possible.
					if (toMaximize) {
						alpha = Math.max(bestMove.score, alpha);
						if (bestMove.score >= beta || bestMove.score == MAX_SCORE)
							return;
					} else {
						beta = Math.min(bestMove.score, beta);
						if (bestMove.score <= alpha || bestMove.score == -MAX_SCORE)
							return;
					}
					if (currDepth == 0)
						i = (i - ThreadID)/NUM_OF_THREADS;
				}
				if (UseTransTable)
					updateTransTable(boardKey, transTableKey, alpha, beta, 
						 currDepth, bestMove.score, TransTable);
			}
		}

		@Override
		public ScoredClobberMove call() throws Exception {
			alphaBeta(thisState, 0, CurBoardKey, 
					Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
			String transTableStr = "";
			if (UseTransTable)
				transTableStr = "\nTransTable size: " + TransTable.size()
						+ "\nTransTable hits: " + transTableHits
						+ "\nTransTable misses: " + transTableMisses
						+ "\nTransTable hit percentage: "
						+ (int)((double)transTableHits/(transTableHits+transTableMisses+0.0000001)*100) + "%";
			System.out.println("-------Thread: " + ThreadID + " -------"
					+ "\nMove Score: " + MvStack[0].score
					+ "\nDepth Reached: " + (maxDepthReached+1)
					+ "\nScoreTable size: " + ScoreTable.size()
					+ "\nScoreTable hits: " + scoreTableHits
					+ "\nScoreTable misses: " + scoreTableMisses
					+ "\nScoreTable hit percentage: " 
					+ (int)((double)scoreTableHits/(scoreTableHits+scoreTableMisses+0.0000001)*100) + "%"
					+ transTableStr
					+ "\n------------------");
			return MvStack[0] ;
		}
		
	}
	
	public class ScoredClobberMove extends ClobberMove 
				 implements Comparable<ScoredClobberMove> {
		
		public double score;
		
		public int compareTo(ScoredClobberMove mv) {
			// TODO Auto-generated method stub
			double diff = this.score - mv.score;
			if (diff > 0) {
				return 1;
			} else if (diff < 0) {
				return -1;
			} else {
				return 0;
			}
		}
		
		public ScoredClobberMove() {
			super();
		}
		public ScoredClobberMove(int r1, int c1, int r2, int c2) {
			row1 = r1;
			col1 = c1;
			row2 = r2;
			col2 = c2;
		}
		public void set(ClobberMove mv, double s)
		{
			row1 = mv.row1;
			col1 = mv.col1;
			row2 = mv.row2;
			col2 = mv.col2;
			score = s;
		}
		public void set(double s) {
			score = s;
		}
		public ScoredClobberMove clone() {
			ScoredClobberMove mv = new ScoredClobberMove(row1, col1, row2, col2);
			mv.set(score);
			return mv;
		}
	}
	
	public class Block {
		public int homeCount;
		public int awayCount;
		public double homeScore;
		public double awayScore;
		
		public Block() {
			homeCount = 0;
			awayCount = 0;
			homeScore = 0;
			awayScore = 0;
		}
	}
	
	public class ScoreTableEntry {
		public int boardKey;
		public double score;
		
		public ScoreTableEntry() {
			boardKey = 0;
			score = 0;
		}
		public ScoreTableEntry(int k, double s) {
			boardKey = k;
			score = s;
		}
	}
	
	public enum TranspositionType {
		UPPERBOUND,
		EXACT_VALUE,
		LOWERBOUND
	}
	
	public class TransTableEntry {
		public int boardKey;
		public int depth;
		public double score;
		TranspositionType type;
		
		public TransTableEntry() {
			boardKey = 0;
			depth = 0;
			score = 0;
			type = TranspositionType.EXACT_VALUE;
		}
		
		public TransTableEntry(int k, int d, double s) {
			boardKey = k;
			depth = d;
			score = s;
		}
	}
	
	public class KeyBoardPair {
		int boardKey;
		ClobberState board;
		
		public KeyBoardPair(int k, ClobberState b) {
			boardKey = k;
			board = b;
		}
	}
	
	
	public ABMODDNETTSTMTSubmitVersion(String n) {
		super(n, new ClobberState(), false);
	}
	
	@Override
	public void startGame(String opponent) {
		Executor = Executors.newFixedThreadPool(NUM_OF_THREADS);
		CurBoardKey = generateInitialBoardKey();
		UseTransTable = false;
		TimeLeft = (double)tournamentParams.integer("GAMETIME");
	}
	
	@Override
	public void endGame(int result) {
		Executor.shutdown();
	}
	
	@Override
	public void init()
	{
		KeyTable = new int[GameState.Who.values().length]
						  [ClobberState.ROWS][ClobberState.COLS];
		generatePiecesKeys();
	}
	
	@Override
	public void timeOfLastMove(double secs) {
		TimeLeft -= secs;
	}
	
	@Override
	public GameMove getMove(GameState state, String lastMv) {
		side = state.getWho();
		gameState = state;
		BestMove = new ScoredClobberMove();
		LastBestMove = BestMove;
		if (state.numMoves >= WHEN_TO_USE_TRANSTABLE)
			UseTransTable = true;
		if (!lastMv.equals("--")) {
			GameState.Who opponent = side == GameState.Who.HOME 
					? GameState.Who.AWAY : GameState.Who.HOME;
			ScoredClobberMove mv = new ScoredClobberMove();
			mv.parseMove(lastMv);
			CurBoardKey = computeBoardKey(CurBoardKey, mv, opponent);
		}
		// fixed first move
		if (state.numMoves == 0)
			fixedFirstMove();
		else
			runThreads();
		CurBoardKey = computeBoardKey(CurBoardKey, BestMove, side);
		System.out.println("-----Move score: " + BestMove.score + "-----");
		return BestMove;
	}
	
	private void runThreads() {
		double timeLapse = 0;
		AlphaBetaThread[] threadPool = new AlphaBetaThread[NUM_OF_THREADS];
		for (int i = 0; i < NUM_OF_THREADS; i++) 
			threadPool[i] = new AlphaBetaThread((ClobberState)gameState.clone(), i);
		int depthUpperLimit = gameState.numMoves + DEPTH_BASE;
		int timeLowerLimit = gameState.numMoves + TIME_BASE;
		int timeUpperLimit = (int) (TIME_CONSTANT - gameState.numMoves > TIME_FACOTR
				? TimeLeft/(TIME_CONSTANT - gameState.numMoves) : TimeLeft/TIME_FACOTR);
		
		for (int d = depthUpperLimit - 2; d <= depthUpperLimit; d++) {
			DepthLimit = gameState.numMoves == 1 ? DEPTH_BASE : d;
			if (side == GameState.Who.HOME)
				BestMove.score = Double.NEGATIVE_INFINITY;
			else
				BestMove.score = Double.POSITIVE_INFINITY;
			
			List<Future<ScoredClobberMove>> futureList = new ArrayList<Future<ScoredClobberMove>>();
			double startTime = System.currentTimeMillis() / 1000;
			for (AlphaBetaThread t : threadPool)
				futureList.add(Executor.submit(t));
			for (Future<ScoredClobberMove> f : futureList) {
				try {
					ScoredClobberMove mv = f.get((int)Math.min(timeUpperLimit,TimeLeft
							-timeLapse-Math.max((TIME_BASE-gameState.numMoves), 
							MINIMUM_REMAINING_TIME)),TimeUnit.SECONDS);
					if (side == GameState.Who.HOME)
						BestMove = BestMove.score >= mv.score ? BestMove : mv;
					else 
						BestMove = BestMove.score <= mv.score ? BestMove : mv;
				} catch (TimeoutException | ExecutionException | InterruptedException e) {
					for (Future<ScoredClobberMove> future : futureList)
						future.cancel(true);
					BestMove = LastBestMove;
					return;
				}
			}
			if (gameState.numMoves == 1)
				return;
			LastBestMove = BestMove.clone();
			double curTime = System.currentTimeMillis() / 1000;
			timeLapse += curTime - startTime;
			if (curTime - startTime > timeLowerLimit
				|| (int)Math.abs(BestMove.score) == MAX_SCORE) {
				return;
			}
		}
	}
	
	private int computeBoardKey (int k, ClobberMove mv, GameState.Who who) {
		GameState.Who opponent = who == GameState.Who.HOME 
					? GameState.Who.AWAY : GameState.Who.HOME;
		k ^= KeyTable[opponent.ordinal()][mv.row2][mv.col2];
		k ^= KeyTable[who.ordinal()][mv.row1][mv.col1];
		k ^= KeyTable[who.ordinal()][mv.row2][mv.col2];
		return k;
	}
	
	private void generatePiecesKeys() {
		Random rand = new Random();
		for (int r = 0; r < ClobberState.ROWS; r++) {
			for (int c = 0; c < ClobberState.COLS; c++) {
				for (int i = 0; i < GameState.Who.values().length; i++) {
					KeyTable[i][r][c] = rand.nextInt(KEYRANGE);
				}
			}
		}
	}
	
	private int generateInitialBoardKey() {
		int key = 0;
		for (int r = 0; r < ClobberState.ROWS; r++) {
			for (int c = 0; c < ClobberState.COLS; c++) {
				key ^= KeyTable[(r+c)%2][r][c];
			}
		}
		return key;
	}
	
	private void fixedFirstMove() {
		BestMove = new ScoredClobberMove(2, 0, 2, 1);
		if (!gameState.moveOK(BestMove)) {
			BestMove.row1 = 3;
			BestMove.row2 = 3;
		}
	}
	
	public static void main(String [] args)
	{
		GamePlayer p = new ABMODDNETTSTMTSubmitVersion("Ominiscience");
		p.compete(args);
	}
}
