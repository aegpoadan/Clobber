package clobber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import game.*;

public class AlphaBetaClobberPlayer extends GamePlayer implements Runnable {
	private static ArrayList<AlphaBetaClobberPlayer> players = new ArrayList<AlphaBetaClobberPlayer>();
	private static ArrayList<Thread> threadPool = new ArrayList<Thread>();
	private static ArrayList<ClobberState> states = new ArrayList<ClobberState>();
	private ClobberState bestState;
	private int threadId;
	private static int numCores = Runtime.getRuntime().availableProcessors();
	private boolean fresh;
	private static int stateIndex;
	private static int bestThread;
	private static boolean multiThreaded = true;
	private ScoredClobberMove[] bestMVStack;

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
		
		public ScoredClobberMove(int r1, int c1, int r2, int c2, double s) {
			row1 = r1;
			col1 = c1;
			row2 = r2;
			col2 = c2;
			score = s;
		}
		public ScoredClobberMove(int r1, int c1, int r2, int c2) {
			row1 = r1;
			col1 = c1;
			row2 = r2;
			col2 = c2;
		}
		public ScoredClobberMove(ScoredClobberMove m) {
			super(m.row1, m.col1, m.row2, m.col2);
			score = m.score;
		}
		
		public void set(ClobberMove mv, double s) {
			row1 = mv.row1;
			col1 = mv.col1;
			row2 = mv.row2;
			col2 = mv.col2;
			score = s;
		}
		public void set(double s) {
			score = s;
		}
	}

	//hardcode
	public static final int MAX_SCORE = 10000;
	public static final int MAX_DEPTH = 50;
	private ScoredClobberMove[] mvStack;
	public int depthLimit = 6;
	
	public AlphaBetaClobberPlayer(String n) {
		super(n, new ClobberState(), false);
	}
	
	//When multi-threading, each thread needs its own gameState and mvStack
	public AlphaBetaClobberPlayer(String n, GameState gs, int threadId, boolean fresh) {
		super(n, gs, false);
		this.threadId = threadId;
		this.fresh = fresh;
	}
	
	@Override
	public GameMove getMove(GameState state, String lastMv) {
		ArrayList<AlphaBetaClobberPlayer> z = players;
		clearThreads();
		if (multiThreaded) {
			expandNode((ClobberState)state);
			try {
				runThreads();
				System.out.println(players.get(bestThread).bestMVStack[0].score);
				return players.get(bestThread).bestMVStack[0];
			} catch (InterruptedException e) {
				System.err.println("A thread was interrupted!");
				System.err.println("Error running multi-threaded version!");
				clearThreads();
				e.printStackTrace();
			} 
		} else {
			alphaBeta((ClobberState)state, 0, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
			System.out.println(mvStack[0].score);
			return mvStack[0];
		}
		return null;
	}
	
	private static void reOrder(ScoredClobberMove[] mvArray, int count, boolean isToMax) {
		if (isToMax)
			Arrays.sort(mvArray, 0, count - 1, Collections.reverseOrder());
		else
			Arrays.sort(mvArray, 0, count - 1);
	}
	
	private static boolean hasPawn(ClobberState board, char who, int r, int c) {
		if (Util.inrange(r, ClobberState.ROWS-1) && Util.inrange(c, ClobberState.COLS-1)) {
			if (board.board[r][c] == who) {
				return true;
			}
		}
		return false;
	}
	
	private static int checkDiagonals (ClobberState board, char who, int score,
						   int r, int c) {
		// Check to see there're allied pawns at diagonals
		
		score++;
		score = hasPawn(board,who,r-1,c-1)?score+1:score;
		score = hasPawn(board,who,r+1,c-1)?score+1:score;
		score = hasPawn(board,who,r+1,c+1)?score+1:score;
		score = hasPawn(board,who,r-1,c+1)?score+1:score;
		return score;
	}
	
	private static int eval(ClobberState board, char who) {
		int score = 0;
		int count = 0;
		boolean valid = false;
		char opponent = who == ClobberState.homeSym 
						? ClobberState.awaySym : ClobberState.homeSym;
		for (int r = 0; r < board.ROWS; r++) {
			for (int c = 0; c < board.COLS; c++) {
				valid = false;
				if (board.board[r][c] == who) {
					if (hasPawn(board, opponent, r-1, c)){
						valid = true;
						score = checkDiagonals(board, who, score, r, c);
					}
					if (hasPawn(board, opponent, r+1, c)){
						valid = true;
						score = checkDiagonals(board, who, score, r, c);
					}
					if (hasPawn(board, opponent, r, c+1)){
						valid = true;
						score = checkDiagonals(board, who, score, r, c);
					} 
					if (hasPawn(board, opponent, r, c-1)){
						valid = true;
						score = checkDiagonals(board, who, score, r, c);
					}
					if (valid) {
						score++;
						count++;
					}
				}
			}
		}
		return score;
	}
	
	private static int evalBoard(ClobberState board) {
		int score = eval(board, ClobberState.homeSym) - eval(board, ClobberState.awaySym);
		if (Math.abs(score) > MAX_SCORE) {
			System.err.println("Problem with eval");
			System.exit(0);
		}
		return score;
	}
	
	@Override
	public void init()
	{
		mvStack = new ScoredClobberMove[MAX_DEPTH];
		for (int i=0; i<MAX_DEPTH; i++) {
			mvStack[i] = new ScoredClobberMove();
		}
	}
	
	private boolean terminalValue(GameState brd, ScoredClobberMove mv)
	{
		GameState.Status status = brd.getStatus();
		boolean isTerminal = true;
		
		if (status == GameState.Status.HOME_WIN) {
			mv.set(MAX_SCORE);
		} else if (status == GameState.Status.AWAY_WIN) {
			mv.set(-MAX_SCORE);
		} else {
			isTerminal = false;
		}
		return isTerminal;
	}

	private static void shuffle(ScoredClobberMove[] ary, int count)
	{
		for (int i=0; i<count; i++) {
			int spot = Util.randInt(i, count-1);
			ScoredClobberMove tmp = ary[i];
			ary[i] = ary[spot];
			ary[spot] = tmp;
		}
	}

	private int createMoveArray(ClobberState board, ScoredClobberMove[] moveArray) {
		
		int moveCount = 0;
		for (int r = 0; r < ClobberState.ROWS; r++) {
			for (int c = 0; c < ClobberState.COLS; c++) {
				ScoredClobberMove moveUp = new ScoredClobberMove(r, c, r + 1, c);
				ScoredClobberMove moveDown = new ScoredClobberMove(r, c, r - 1,
						c);
				ScoredClobberMove moveLeft = new ScoredClobberMove(r, c, r,
						c - 1);
				ScoredClobberMove moveRight = new ScoredClobberMove(r, c, r,
						c + 1);
				moveCount = addValidMove(board, moveUp, moveArray, moveCount);
				moveCount = addValidMove(board, moveDown, moveArray, moveCount);
				moveCount = addValidMove(board, moveLeft, moveArray, moveCount);
				moveCount = addValidMove(board, moveRight, moveArray, moveCount);
			}
		}
		return moveCount;
	}
	
	private int addValidMove (ClobberState board, ScoredClobberMove mv, ScoredClobberMove[] mvArray, int index) {
		if (board.moveOK(mv)) {
			board.makeMove(mv);
			mv.set(evalBoard(board));
			undoMove(board, mv);
			mvArray[index] = mv;
			return index+1;
		}
		return index;
	}
	
	private void undoMove (ClobberState board, ScoredClobberMove mv) {
		board.board[mv.row1][mv.col1] = board.board[mv.row2][mv.col2];
		board.board[mv.row2][mv.col2] = board.board[mv.row2][mv.col2] == ClobberState.homeSym
										? ClobberState.awaySym : ClobberState.homeSym;
		board.numMoves--;
		board.status = GameState.Status.GAME_ON;
		board.togglePlayer();
	}
	
private void alphaBeta(ClobberState board, int currDepth, double alpha, double beta) {

	boolean toMaximize = (board.getWho() == GameState.Who.HOME);
	boolean toMinimize = !toMaximize;

	boolean isTerminal = terminalValue(board, mvStack[currDepth]);
	
	if (isTerminal) {
		;
	} else if (currDepth == depthLimit) {
		mvStack[currDepth].set(evalBoard(board));
	} else {
		ScoredClobberMove bestMove = mvStack[currDepth];
		ScoredClobberMove nextMove = mvStack[currDepth+1];
		double bestScore = (toMaximize ? 
				Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
		bestMove.set(bestScore);

		ScoredClobberMove[] moveArray 
			= new ScoredClobberMove[ClobberState.ROWS*ClobberState.COLS*2 - board.numMoves*2];
		int moveCount = createMoveArray(board, moveArray);
		if(moveCount == 0) {
			System.out.println("Board when moveCount is 0:\n" + board);
		}
		//shuffle(moveArray, moveCount);
		reOrder(moveArray, moveCount, toMaximize);
		
		for (int i = 0; i < moveCount; i++) {
			ScoredClobberMove mv = moveArray[i];
			board.makeMove(mv);
			alphaBeta(board, currDepth + 1, alpha, beta); // Check out move
			undoMove(board, mv);

			// Check out the results, relative to what we've seen before
			if (toMaximize && nextMove.score > bestMove.score) {
				bestMove.set(mv, nextMove.score);
			} else if (!toMaximize && nextMove.score < bestMove.score) {
				bestMove.set(mv, nextMove.score);
			}

			// Update alpha and beta. Perform pruning, if possible.
			if (toMinimize) {
				beta = Math.min(bestMove.score, beta);
				if (bestMove.score <= alpha || bestMove.score == -MAX_SCORE) {
					return;
				}
			} else {
				alpha = Math.max(bestMove.score, alpha);
				if (bestMove.score >= beta || bestMove.score == MAX_SCORE) {
					return;
				}
			}
		}
	}

}


	
	private void expandNode(ClobberState board) {
		boolean toMaximize = (board.getWho() == GameState.Who.HOME);
		ScoredClobberMove[] moveArray 
		= new ScoredClobberMove[ClobberState.ROWS*ClobberState.COLS*2 - board.numMoves*2];
		
		int moveCount = createMoveArray(board, moveArray);
		//shuffle(moveArray, moveCount);
		reOrder(moveArray, moveCount, toMaximize);
		
		for(int i=0; i<moveCount; i++) {
			ClobberState newBoard = (ClobberState) board.clone();
			boolean okMove = newBoard.makeMove(moveArray[i]);
			ArrayList z = states;
			if(okMove) {
				newBoard.who = (newBoard.who == GameState.Who.HOME) ? GameState.Who.AWAY : GameState.Who.HOME;//Have to flip turn after root expansion
				states.add(newBoard);
			} else {
				System.err.println("Bad move in expandNode");
			}
		}
	}
	
	public static void main(String [] args)
	{
		GamePlayer p = new AlphaBetaClobberPlayer("Alpha-Beta");
		p.init();
		ClobberState state = new ClobberState();
		System.out.println(p.getMove(state, "--"));
		//p.compete(args, 1);
		/*
		p.init();
		CLobberState state = new ClobberState();
		state.makeMove(new Connect4Move(3));
		state.makeMove(new Connect4Move(4));
		state.makeMove(new Connect4Move(4));
		state.makeMove(new Connect4Move(5));
		GameMove mv = p.getMove(state, "");
		System.out.println("Original board");
		System.out.println(state.toString());
		System.out.println("Move: " + mv.toString());
		System.out.println("Board after move");
		state.makeMove(mv);
		System.out.println(state.toString());
		 */
	}

	public void run() {
		this.init();
		while(okToRun()) {
			if(fresh) {
				alphaBeta((ClobberState)super.gameState, 0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
				bestState = (ClobberState)this.gameState;
				fresh = false;
				bestMVStack = this.mvStack;
				resetMVStack();
			} else {
				ClobberState checkMe;
				synchronized(states) {
					checkMe = states.get(stateIndex);
					stateIndex++;
				}
				alphaBeta(checkMe, 0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
				bestState = compareStates();
				resetMVStack();
			}
		}
	}
	
	private boolean okToRun() {
		boolean go;
		synchronized(states) {
			if (stateIndex < states.size() - 1)
				go = true;
			else
				go = false;
		}
		return go;
	}
	
	private ClobberState compareStates()  {
		int current = evalBoard(this.bestState); //Best state found so far by this thread
		int other = evalBoard((ClobberState)super.gameState); //State found by most recent alphabeta
		if(current > other)
			return (ClobberState)super.gameState;
		else {
			bestMVStack = this.mvStack;
			return this.bestState;
		}
	} 
	
	private static int getBestState() {
		int[] best = new int[2]; //[0] is best score, [1] is threadId
		best[0] = -1;
		best[1] = -1;
		ArrayList z = players;
		for(AlphaBetaClobberPlayer p: players) {
			if(p.bestState != null) {
				int score = evalBoard(p.bestState);
				if(score > best[0]) {
					best[0] = score;
					best[1] = p.threadId;
				}
			}
		}
		return best[1];
	}
	
	private void resetMVStack() {
		for (int i=0; i<MAX_DEPTH; i++) {
			mvStack[i] = new ScoredClobberMove();
		}
	}
	
	public static void clearThreads() {
		players.clear();
		threadPool.clear();
		states.clear();
		bestThread = -1;
	}
	
	public static void runThreads() throws InterruptedException {
		ArrayList x = players;
		for(int i=0; i<numCores; i++) {
			AlphaBetaClobberPlayer z = new AlphaBetaClobberPlayer("Alpha-Beta", states.get(i), i, true);
			players.add(z);
		}
		
		for(GamePlayer p: players) {
			threadPool.add(new Thread((Runnable) p));
		}
		for(Thread t: threadPool) {
			t.start();
		}
		for(Thread t: threadPool) {
			t.join();
		}
		bestThread = getBestState();
	}
}
