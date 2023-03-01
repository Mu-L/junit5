/*
 * Copyright 2015-2023 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * https://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.platform.console.options;

import static org.apiguardian.api.API.Status.INTERNAL;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;

import org.apiguardian.api.API;
import org.junit.platform.console.tasks.ConsoleTestExecutor;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IExitCodeGenerator;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Unmatched;

/**
 * @since 1.10
 */
@Command(//
		name = "junit", //
		abbreviateSynopsis = true, //
		sortOptions = false, //
		usageHelpWidth = 95, //
		showAtFileInUsageHelp = true, //
		usageHelpAutoWidth = true, //
		description = "Launches the JUnit Platform for test discovery and execution.", //
		footerHeading = "%n", //
		footer = "For more information, please refer to the JUnit User Guide at%n" //
				+ "@|underline https://junit.org/junit5/docs/current/user-guide/|@", //
		scope = CommandLine.ScopeType.INHERIT, //
		exitCodeOnInvalidInput = CommandResult.FAILURE, //
		exitCodeOnExecutionException = CommandResult.FAILURE //
)
@API(status = INTERNAL, since = "1.10")
public class MainCommand implements Callable<Object>, IExitCodeGenerator {

	private final Function<CommandLineOptions, ConsoleTestExecutor> consoleTestExecutorFactory;
	@Option(names = { "-h", "--help" }, help = true, hidden = true)
	private boolean helpRequested;

	@Option(names = { "--h", "-help" }, help = true, hidden = true)
	private boolean helpRequested2;

	@Unmatched
	private List<String> allParameters = new ArrayList<>();

	@Spec
	CommandSpec commandSpec;

	CommandResult<?> commandResult;

	public MainCommand(Function<CommandLineOptions, ConsoleTestExecutor> consoleTestExecutorFactory) {
		this.consoleTestExecutorFactory = consoleTestExecutorFactory;
	}

	@Override
	public Object call() {
		if (helpRequested || helpRequested2) {
			commandSpec.commandLine().usage(commandSpec.commandLine().getOut());
			commandResult = CommandResult.success();
			return null;
		}
		if (allParameters.contains("--list-engines")) {
			return runCommand("engines", Optional.of("--list-engines"));
		}
		return runCommand("execute", Optional.empty());
	}

	@Override
	public int getExitCode() {
		return commandResult.getExitCode();
	}

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private Object runCommand(String subcommand, Optional<String> triggeringOption) {
		CommandLine commandLine = commandSpec.commandLine();
		commandLine.setUnmatchedArgumentsAllowed(false);
		Object command = commandLine.getSubcommands().get(subcommand).getCommandSpec().userObject();

		List<String> args = new ArrayList<>(commandLine.getParseResult().expandedArgs());
		triggeringOption.ifPresent(args::remove);
		CommandResult<?> result = runCommand(commandLine.getOut(), //
			commandLine.getErr(), //
			args.toArray(new String[0]), //
			command);
		this.commandResult = result;

		printDeprecationWarning(subcommand, triggeringOption, commandLine);

		return result.getValue().orElse(null);
	}

	private static void printDeprecationWarning(String subcommand, Optional<String> triggeringOption,
			CommandLine commandLine) {
		PrintWriter err = commandLine.getErr();
		String reason = triggeringOption.map(it -> " due to use of '" + it + "'").orElse("");

		commandLine.getOut().flush();
		err.println();
		err.println(commandLine.getColorScheme().text(
			String.format("@|yellow,bold WARNING:|@ Delegated to the '%s' command%s.", subcommand, reason)));
		err.println(commandLine.getColorScheme().text(
			"         This behaviour has been deprecated and will be removed in a future release."));
		err.println(
			commandLine.getColorScheme().text("         Please use the '" + subcommand + "' command directly."));
		err.flush();
	}

	public CommandResult<?> run(PrintWriter out, PrintWriter err, String[] args) {
		CommandLine commandLine = new CommandLine(this).addSubcommand(
			new DiscoverTestsCommand(consoleTestExecutorFactory)).addSubcommand(
				new ExecuteTestsCommand(consoleTestExecutorFactory)).addSubcommand(new ListTestEnginesCommand());
		return runCommand(out, err, args, commandLine);
	}

	private static CommandResult<?> runCommand(PrintWriter out, PrintWriter err, String[] args, Object command) {
		return runCommand(out, err, args, new CommandLine(command));
	}

	private static CommandResult<Object> runCommand(PrintWriter out, PrintWriter err, String[] args,
			CommandLine commandLine) {
		int exitCode = commandLine //
				.setOut(out) //
				.setErr(err) //
				.setExecutionExceptionHandler((ex, cmd, parseResult) -> {
					err.println(cmd.getColorScheme().richStackTraceString(ex));
					err.println();
					err.flush();
					cmd.usage(out);
					return CommandResult.FAILURE;
				}) //
				.setCaseInsensitiveEnumValuesAllowed(true) //
				.setAtFileCommentChar(null) // for --select-method com.acme.Foo#m()
				.execute(args);
		return CommandResult.create(exitCode, commandLine.getExecutionResult());
	}
}
