package pomdp.utilities;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.Map.Entry;
import pomdp.environments.POMDP;
import pomdp.environments.POMDP.RewardType;

public class POMDPLoader {
	private POMDP m_pPOMDP;
	public POMDPLoader( POMDP pomdp ){
		m_pPOMDP = pomdp;
	}	
	
	public void load( String sFileName ) throws IOException, InvalidModelFileFormatException{
		Logger.getInstance().logln( "Started loading model " + sFileName );
		LineReader lrInput = new LineReader(sFileName );
		String sLine = "";//按行读
		String sType = "";//数据类型
		String sValue;//数据值
		StringTokenizer stLine;
		int cLines = 0;
		
		try{
			readHeader( lrInput );	//读取头文件
		}
		catch( EndOfFileException e ){
			throw new InvalidModelFileFormatException( "Missing header parameters" );
		}
		
		while( !lrInput.endOfFile() ){//读取函数
			sLine = lrInput.readLine();
			cLines++;
			if( cLines % 1000 == 0 ){
				Logger.getInstance().log( "." );
			}
			if( ( sLine.length() > 0 ) && ( sLine.charAt( 0 ) != '#' ) ){
				stLine = new StringTokenizer( sLine );
				if( stLine.hasMoreTokens() ){
					sType = stLine.nextToken();
					if( sType.equals( "T:" ) ){
						sValue = stLine.nextToken();
						readTransition( lrInput, sValue, stLine );//读取转移函数
					}
					else if( sType.equals( "O:" ) ){
						sValue = stLine.nextToken();
						readObservation( lrInput, sValue, stLine );//读取观察值函数
					}
					else if( sType.equals( "R:" ) ){
						readReward( lrInput, stLine );//读取回报值函数
					}
					else if( sType.equals( "start:" ) ){
						if( stLine.hasMoreTokens() )
							readStartState( lrInput, stLine );//读取开始状态
						else
							readStartState( lrInput, null );
					}
					else if( sType.equals( "E:" ) ){
						if( stLine.hasMoreTokens() )
							readTerminalStates( stLine );//读取终止状态
					}
					else if( sType.equals( "OS:" ) ){
						if( stLine.hasMoreTokens() )
							readObservationStates( stLine );//读取观察值敏感状态
					}
				}
			}
		}
				
		verifyFunctions();
        //m_pPOMDP.getM_FReward().printD2();
		Logger.getInstance().logln( "Done loading model" );
	}
	
	/**
	 * 检测各概率之和是否超过1
	 */
	protected void verifyFunctions(){
		int iStartState = 0, iAction = 0, iEndState = 0;
		Iterator<Entry<Integer,Double>> itNonZero = null;
		Entry<Integer,Double> e = null;
		double dTr = 0.0, dSumTr = 0.0, dO = 0.0, dSumO = 0.0, dPr = 0.0, dSumPr = 0.0;
		boolean bFixed = false;
		int cStates = m_pPOMDP.getStateCount();
		int cActions = m_pPOMDP.getActionCount();
		
		itNonZero = m_pPOMDP.getStartStates();//得到概率非0起始状态	
		while( itNonZero.hasNext() ){
			e = itNonZero.next();
			if( e != null ){
				iStartState = e.getKey();
				dPr = e.getValue();
				dSumPr += dPr;
			}
		}
		if( Math.abs( dSumPr - 1.0 ) > 0.0001 )
			Logger.getInstance().logln( "sum of start state probs = " + dSumPr );
		
		bFixed = false;
		for( iStartState = 0 ; iStartState < cStates ; iStartState++ ){
			for( iAction = 0 ; iAction < cActions ; iAction++ ){
				dSumTr = 0.0;
				itNonZero = m_pPOMDP.getNonZeroTransitions( iStartState, iAction );//获得和开始状态和动作有关的概率非0的转换
				while( itNonZero.hasNext() ){
					e = itNonZero.next();
					iEndState = e.getKey();
					dTr = e.getValue();
					dSumTr += dTr;
				}
				
				if( dSumTr == 0.0 ){//获得和为0，设置原地不动概率为1
					m_pPOMDP.setTransition( iStartState, iAction, iStartState, 1.0 );
					dSumTr = 1.0;
					bFixed = true;
				}
				
				if( Math.abs( dSumTr - 1.0 ) > 0.0001 )
					Logger.getInstance().logln( "sum tr( " + m_pPOMDP.getStateName( iStartState ) + ", " + iAction + ", * ) = " + dSumTr );
			}
		}
		if( bFixed ){
			Logger.getInstance().logln( "Model file corrupted - needed to fix some transition values" );
		}
		
		for( iAction = 0 ; iAction < cActions ; iAction++ ){
			for( iEndState = 0 ; iEndState < cStates ; iEndState++ ){
				dSumO = 0.0;
				itNonZero = m_pPOMDP.getNonZeroObservations( iAction, iEndState );
				while( itNonZero.hasNext() ){
					e = itNonZero.next();
					dO = e.getValue();
					dSumO += dO;
				}
				if( Math.abs( dSumO - 1.0 ) > 0.0001 )
					Logger.getInstance().logln( "sum O( " + iAction + ", " + iEndState + ", * ) = " + dSumO );
			}
		}
	}
	
	/**
	 * Supporting the format:
	 * E: <line of terminal states>
	 */
	private void readTerminalStates( StringTokenizer stLine ){
		String sTerminalState = "";
		int iTerminalState = 0;
		
		while( stLine.hasMoreElements() ){
			sTerminalState = stLine.nextToken();
			iTerminalState = m_pPOMDP.getStateIndex( sTerminalState );//得到终止状态索引
			m_pPOMDP.addTerminalState( iTerminalState );//增加终止状态
		}	
	}

	/**
	 * Supporting the format:
	 * OS: <line of observation sensitive states>
	 */
	private void readObservationStates( StringTokenizer stLine ){
		String sObservationState = "";
		int iObservationState = 0;
		
		while( stLine.hasMoreElements() ){
			sObservationState = stLine.nextToken();
			iObservationState = m_pPOMDP.getStateIndex( sObservationState );
			m_pPOMDP.addObservationSensitiveState( iObservationState );//添加观察值敏感状态
		}	
	}

	/**
	 * Supporting the format:
	 * start:
	 * line of distributions
	 */
	protected void readStartState( LineReader lrInput, StringTokenizer stLine )  throws InvalidModelFileFormatException{
		String sValue = "", sLine;
		int iStartState = 0;
		double dValue = 0;
		double dSumProbs = 0.0;
		
		if( stLine == null ){
			try{
				sLine = lrInput.readLine();
				stLine = new StringTokenizer( sLine );
			}
			catch( IOException e ){
				throw new InvalidModelFileFormatException( "Start: must be followed by a line of distributions." );
			}
		}
		try{
			if(stLine.countTokens() == 1)//计算在生成异常之前可以调用此 tokenizer的 nextToken 方法的次数。
			{
				if (stLine.nextToken().equals("uniform"))//当下一段为uniform时调用,均分概率
				{
					for( iStartState = 0 ; iStartState < m_pPOMDP.getStateCount() ; iStartState++ ){
						dValue = 1.0/m_pPOMDP.getStateCount();
						dSumProbs += dValue;
						m_pPOMDP.setStartStateProb( iStartState, dValue );
					}
				}
			}
			else {

				for( iStartState = 0 ; iStartState < m_pPOMDP.getStateCount() ; iStartState++ ){
					sValue = stLine.nextToken();
					dValue = Double.parseDouble( sValue );//状态为iStartState时的概率
					dSumProbs += dValue;//累加概率
					m_pPOMDP.setStartStateProb( iStartState, dValue );//读取开始概率
				}
			}
			if( Math.abs( 1.0 - dSumProbs ) > 0.001 ){//判断累加概率是否逼进1，如果与1相差过大则按比例缩小放大
				Iterator<Entry<Integer,Double>> itStartStates = m_pPOMDP.getStartStates();
				Entry<Integer,Double> eStartState = null;
				while( itStartStates.hasNext() ){
					eStartState = itStartStates.next();
					if( eStartState != null ){
						iStartState = eStartState.getKey();
						dValue = eStartState.getValue();
						m_pPOMDP.setStartStateProb( iStartState, dValue / dSumProbs );
					}
				}
			}
		}
		
		catch( NoSuchElementException e ){
			throw new InvalidModelFileFormatException( "insufficient number of probabilities" );
		}	
	}
	
	/**
	 * Supporting the following formats:
	 * T: <action>  followed by a transition matrix
	 * T: <action> : <start state>  followed by a single line of transitions
	 * T: <action> : <start state> : <end state> %f
	 * TODO - add support for a wildcard asterix (*)
	 */
	protected void readTransition( LineReader lrInput, String sAction, StringTokenizer stLine ) throws InvalidModelFileFormatException{
		String sStartState = "", sEndState = "", sValue = "", sLine = "";
		int iStartState = 0, iEndState = 0, iActionIdx = m_pPOMDP.getActionIndex( sAction ), iAction = 0;
		//概率值
		double dValue = 0;
		int cStates = m_pPOMDP.getStateCount(), cActions = m_pPOMDP.getActionCount();
		
		if( stLine.hasMoreTokens() ){
			//读开始状态
			stLine.nextToken();
			sStartState = stLine.nextToken();
			if( sStartState.equals( "*" ) )
				iStartState = -1;
			else
				iStartState = m_pPOMDP.getStateIndex( sStartState );
			if( stLine.hasMoreTokens() ){
				//读结束状态
				stLine.nextToken();
				sEndState = stLine.nextToken();
				sValue = stLine.nextToken();
				if( sEndState.equals( "*" ) )
					iEndState = -1;
				else
					iEndState = m_pPOMDP.getStateIndex( sEndState );
				//读概率值
				dValue = Double.parseDouble( sValue );
				if(( dValue == 0.0 ) && (sAction.equals("*")))
					return;
				//动作在本函数被调用前就读出来了
				//增加*动作下的转换，把所有动作下的情况都加上去
				//动作和开始状态不会同时为*
				if( sAction.equals( "*" ) ){
					for( iAction = 0 ; iAction < cActions ; iAction++ ){
						//使用index指定开始状态、动作、结束状态
						m_pPOMDP.setTransition( iStartState, iAction, iEndState, dValue );
					}
				}
				else{
					//增加*开始状态的转换，把所有开始状态的情况都加上去
					if( sStartState.equals( "*" ) ){
						for( iStartState = 0 ; iStartState < cStates ; iStartState++ ){
							m_pPOMDP.setTransition( iStartState, iActionIdx, iEndState, dValue );
						}
					}
					//一个明确的转换
					else{
						m_pPOMDP.setTransition( iStartState, iActionIdx, iEndState, dValue );
					}
				}
			
			}
			//只有一个开始状态
			//对应如下情况：
			//T: <action> : <start-state> 
			//%f %f ... %f 
			else{
				try{
					sLine = lrInput.readLine();
					stLine = new StringTokenizer( sLine );
					for( iEndState = 0 ; iEndState < cStates ; iEndState++ ){
						sValue = stLine.nextToken();
						dValue = Double.parseDouble( sValue );
						
						if( dValue != 0.0 ){
							if( sAction.equals( "*" ) ){
								for( iAction = 0 ; iAction < cActions ; iAction++ ){
									m_pPOMDP.setTransition( iStartState, iAction, iEndState, dValue );
								}
							}
							else{
								m_pPOMDP.setTransition( iStartState, iActionIdx, iEndState, dValue );
							}
						}
					}
				}
				catch( NoSuchElementException e ){
					throw new InvalidModelFileFormatException( "insufficient number of transitions " + sLine );
				}
				catch( IOException e ){
					throw new InvalidModelFileFormatException();
				}
			}
		}
		//只有一个动作
		else{		
			try{
				sLine = lrInput.readLine();

				for( iStartState = 0 ; iStartState < cStates ; iStartState++ ){

					//概率均分的情况
					//状态不会改变的情况
					//T: <action> 
					//uniform
					if (sLine.equals("uniform"))
					{
						for( iEndState = 0 ; iEndState < cStates ; iEndState++ ){
							
							dValue = 1.0/m_pPOMDP.getStateCount();

							if( sAction.equals( "*" ) ){
								for( iAction = 0 ; iAction < cActions ; iAction++ ){
									m_pPOMDP.setTransition( iStartState, iAction, iEndState, dValue );
								}
							}
							else{
								m_pPOMDP.setTransition( iStartState, iActionIdx, iEndState, dValue );
							}						
						}
						
					}
					
					//状态不会改变的情况
					//T: <action> 
					//identity
					else if (sLine.equals("identity"))
					{
						for( iEndState = 0 ; iEndState < cStates ; iEndState++ ){
							
							if (iStartState == iEndState)
								dValue = 1;
							else
								dValue = 0;

							if( sAction.equals( "*" ) ){
								for( iAction = 0 ; iAction < cActions ; iAction++ ){
									m_pPOMDP.setTransition( iStartState, iAction, iEndState, dValue );
								}
							}
							else{
								m_pPOMDP.setTransition( iStartState, iActionIdx, iEndState, dValue );
							}						
						}
						
					}
					
					//对应如下情况：
					//T: <action> 
					//%f %f ... %f 
					//%f $f ... %f 
					//...
					//%f $f ... %f
					else {

						stLine = new StringTokenizer( sLine );
						for( iEndState = 0 ; iEndState < cStates ; iEndState++ ){
							sValue = stLine.nextToken();
							dValue = Double.parseDouble( sValue );

							if( dValue != 0.0 ){
								if( sAction.equals( "*" ) ){
									for( iAction = 0 ; iAction < cActions ; iAction++ ){
										m_pPOMDP.setTransition( iStartState, iAction, iEndState, dValue );
									}
								}
								else{
									m_pPOMDP.setTransition( iStartState, iActionIdx, iEndState, dValue );
								}
							}
						}	
						sLine = lrInput.readLine();
					}
				}
			}
			catch( NoSuchElementException e ){
				throw new InvalidModelFileFormatException( "insufficient number of transitions " + sLine );
			}
			catch( IOException e ){
				throw new InvalidModelFileFormatException();
			}
		}
	}

	/**
	 * Supporting the following formats:
	 * R: <action> : <start state> : <end state> : <observation> %f
	 * supporting wildcards asterix (*)不同类型存入到回报函数不同维度变量中
	 */
	protected void readReward( LineReader lrInput, StringTokenizer stLine ) throws InvalidModelFileFormatException{
		try{
			String sAction = stLine.nextToken();
			stLine.nextToken();
			String sStartState = stLine.nextToken();
			stLine.nextToken();
			String sEndState = stLine.nextToken();
			stLine.nextToken();
			String sObservation = stLine.nextToken();
			String sValue = stLine.nextToken();
			int iStartState = 0, iEndState = 0, iAction = 0;
			int iSpecifiedStartState = -1, iSpecifiedEndState = -1, iSpecifiedAction = -1;//用来判断回报值类型
			double dValue = Double.parseDouble( sValue );
			int cStates = m_pPOMDP.getStateCount(), cActions = m_pPOMDP.getActionCount();
			
			m_pPOMDP.setMinimalReward( -1, dValue );
	        //观察值一定是*
			if( !sObservation.equals( "*" ) )
				throw new InvalidModelFileFormatException( "Not supporting splitting rewards to observations" );
			if( !sAction.equals( "*" ) )
				iSpecifiedAction = m_pPOMDP.getActionIndex( sAction );
			if( !sStartState.equals( "*" ) )
				iSpecifiedStartState = m_pPOMDP.getStateIndex( sStartState );
			if( !sEndState.equals( "*" ) )
			{
				iSpecifiedEndState = m_pPOMDP.getStateIndex( sEndState ); //R(s,a,s')
			}

			/* R(s,a,s')*/
			if( iSpecifiedEndState != -1 ){
				m_pPOMDP.setRewardType( RewardType.StateActionState );
				for( iStartState = 0 ; iStartState < cStates ; iStartState++ ){
					if( ( iSpecifiedStartState == -1 ) || ( iStartState == iSpecifiedStartState ) ){
						for( iAction = 0 ; iAction < cActions ; iAction++ ){
							if( ( iSpecifiedAction == -1 ) || ( iSpecifiedAction == iAction ) ){
								//m_pPOMDP.addEndState(iSpecifiedEndState, dValue);
								m_pPOMDP.setReward( iStartState, iAction, iSpecifiedEndState, dValue );
								m_pPOMDP.setMinimalReward( iAction, dValue );
							}
						}
					}
				}
			}
			/*R(s,a)*/
			else if( iSpecifiedAction != -1 ){
				m_pPOMDP.setRewardType( RewardType.StateAction );
				m_pPOMDP.setMinimalReward( iSpecifiedAction, dValue );
				for( iStartState = 0 ; iStartState < cStates ; iStartState++ ){
					if( ( iSpecifiedStartState == -1 ) || ( iStartState == iSpecifiedStartState ) ){
						m_pPOMDP.setReward( iStartState, iSpecifiedAction, dValue );
					}
				}
			}
			/*R(s)*/
			else if( iSpecifiedStartState != -1 ){
				m_pPOMDP.setRewardType( RewardType.State );
				m_pPOMDP.setReward( iSpecifiedStartState, dValue );
				for( iAction = 0 ; iAction < cActions ; iAction++ ){
					m_pPOMDP.setMinimalReward( iAction, dValue );
				}
			}
			/* all rewards R*/
			else if (sAction.equals("*") && sStartState.equals("*") && sEndState.equals("*") && sObservation.equals("*"))
			{
				for( iStartState = 0 ; iStartState < cStates ; iStartState++ ) {				
					m_pPOMDP.setReward( iStartState, dValue );
					for( iAction = 0 ; iAction < cActions ; iAction++ ){
						m_pPOMDP.setMinimalReward( iAction, dValue );
						m_pPOMDP.setReward( iStartState, iAction, dValue );
						for( iEndState = 0 ; iEndState < cStates ; iEndState++ ){
							m_pPOMDP.setReward( iStartState, iAction, iEndState, dValue );
						}
					}
				}
				
				
			}
				
			else{
				throw new InvalidModelFileFormatException( "Format must be - R: <action> : <state> : <state> : * %f" );
			}
		}
		catch( NoSuchElementException e ){
			throw new InvalidModelFileFormatException( "Format must be - R: <action> : <state> : <state> : * %f" );
		}
	}
	/**
	 * Supporting the following formats:
	 * O: <action> : <end state> followed by an observation matrix
	 * O: <action> : <end state> followed by a single line of observation
	 * O: <action> : <end state> : <observation> %f
	 * TODO - add support for a wildcard asterix (*)
	 */
	protected void readObservation( LineReader lrInput, String sAction, StringTokenizer stLine ) throws InvalidModelFileFormatException{
		String sObservation = "", sEndState = "", sValue = "", sLine = "";
		int iObservation = 0, iEndState = 0, iActionIdx = m_pPOMDP.getActionIndex( sAction );
		double dValue = 0;
		
		if( stLine.hasMoreTokens() ){
			//读结束状态
			stLine.nextToken(); //":"
			sEndState = stLine.nextToken();
			if( sEndState.equals( "*" ) )
				iEndState = -1;
			else
				iEndState = m_pPOMDP.getStateIndex( sEndState );
			
			
			//如下情况：
			//O : <action> : <end-state> : <observation> %f
			if( stLine.hasMoreTokens() ){
				//读观察值
				stLine.nextToken();//":"
				sObservation = stLine.nextToken();
				iObservation = m_pPOMDP.getObservationIndex( sObservation );
				
				if( iObservation == -1 && !sObservation.equals( "*" ) )
					throw new InvalidModelFileFormatException( "Observation " + sObservation + " was not recognized" ); 
				
				sValue = stLine.nextToken();
				//读概率值
				dValue = Double.parseDouble( sValue );
								
				if( sEndState.equals( "*" ) )
					iEndState = -1;
				
				if( sAction.equals( "*" ) )
					iActionIdx = -1;
				
				m_pPOMDP.setObservation( iActionIdx, iEndState, iObservation, dValue );
			}
			//如下情况：
			//O : <action> : <end-state>
			//%f %f ... %f
			else{
				try{
					sLine = lrInput.readLine();
					stLine = new StringTokenizer( sLine );
					for( iObservation = 0 ; iObservation < m_pPOMDP.getObservationCount() ; iObservation++ ){
						sValue = stLine.nextToken();
						dValue = Double.parseDouble( sValue );

						if( sAction.equals( "*" ) )
							iActionIdx = -1;
						m_pPOMDP.setObservation( iActionIdx, iEndState, iObservation, dValue );
					}
				}
				catch( NoSuchElementException e ){
					throw new InvalidModelFileFormatException( "insufficient number of observations " + sLine );
				}
				catch( IOException e ){
					throw new InvalidModelFileFormatException();
				}
			}
		}
		//如下情况：N*O
		//O : <action>
		//%f %f ... %f 
		//%f $f ... %f 
		//...
		//%f $f ... %f
		else{
			try{
				for( iEndState = 0 ; iEndState < m_pPOMDP.getStateCount() ; iEndState++ ){
					sLine = lrInput.readLine();
					stLine = new StringTokenizer( sLine );
					for( iObservation = 0 ; iObservation < m_pPOMDP.getObservationCount() ; iObservation++ ){
						sValue = stLine.nextToken();
						dValue = Double.parseDouble( sValue );
						
						if( sAction.equals( "*" ) )
							iActionIdx = -1;
						m_pPOMDP.setObservation( iActionIdx, iEndState, iObservation, dValue );
						
					}				
				}
			}
			catch( NoSuchElementException e ){
				throw new InvalidModelFileFormatException( "insufficient number of observations " + sLine );
			}
			catch( IOException e ){
				throw new InvalidModelFileFormatException();
			}
		}
	}

	protected void readHeader( LineReader lrInput ) throws IOException{
		String sLine = "";
		String sType = "", sValue;
		int cVars = 0;
		StringTokenizer stLine;//StringTokenizer是用来分隔String的应用类
		
		while( cVars < 5 ){//5是头文件中要读取的种类个数
			sLine = "";
			while( sLine.equals( "" ) ){
				sLine = lrInput.readLine();;
				sLine = sLine.trim();//删除字符串中多余的空格
			}
			stLine = new StringTokenizer( sLine );
			sType = stLine.nextToken();
			if( !sType.equals( "#" ) ){ //判断是否是注释
				if( sType.equals( "discount:" ) ){
					sValue = stLine.nextToken();
					m_pPOMDP.setDiscountFactor( Double.parseDouble( sValue ) );//设置折扣参数
					cVars++;
				}
				else if( sType.equals( "values:" ) ){
					cVars++;
				}
				else if( sType.equals( "states:" ) ){
					sValue = stLine.nextToken();

					try{
						m_pPOMDP.setStateCount( Integer.parseInt( sValue ) );//如果不是数字，就执行 catch语句
					}
					catch( NumberFormatException e ){
						m_pPOMDP.addState( sValue );//添加状态
						while( stLine.hasMoreTokens() ){
							sValue = stLine.nextToken();
							m_pPOMDP.addState( sValue );
						}
					}
					Logger.getInstance().log( "|S| = " + m_pPOMDP.getStateCount() );
					cVars++;
				}
				else if( sType.equals( "actions:" ) ){
					sValue = stLine.nextToken();

					try{
						m_pPOMDP.setActionCount( Integer.parseInt( sValue ) );//如果不是数字，就执行 catch语句
					}
					catch( NumberFormatException e ){
						m_pPOMDP.addAction( sValue );//添加动作
						while( stLine.hasMoreTokens() ){
							sValue = stLine.nextToken();
							m_pPOMDP.addAction( sValue );
						}
					}
					Logger.getInstance().log( "|A| = " + m_pPOMDP.getActionCount() );
					
					cVars++;
				}
				else if( sType.equals( "observations:" ) ){
					sValue = stLine.nextToken();

					try{
						m_pPOMDP.setObservationCount( Integer.parseInt( sValue ) );//如果不是数字，就执行 catch语句
					}
					catch( NumberFormatException e ){
						m_pPOMDP.addObservation( sValue );//添加观察值
						while( stLine.hasMoreTokens() ){
							sValue = stLine.nextToken();
							m_pPOMDP.addObservation( sValue );
						}
					}
					Logger.getInstance().log( "|O| = " + m_pPOMDP.getObservationCount() );
					
					cVars++;
				}
					 
			}
		}
		Logger.getInstance().logln();
		m_pPOMDP.initDynamicsFunctions();
	}

}
