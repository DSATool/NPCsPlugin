/*
 * Copyright 2017 DSATool team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package npcs;

import dsatool.gui.Main;
import dsatool.plugins.Plugin;
import npcs.ui.NPCsController;

/**
 * A plugin for managing non-player characters
 *
 * @author Dominik Helm
 */
public class NPCs extends Plugin {

	private NPCsController controller;

	/*
	 * (non-Javadoc)
	 *
	 * @see plugins.Plugin#getPluginName()
	 */
	@Override
	public String getPluginName() {
		return "NPCs";
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see plugins.Plugin#initialize()
	 */
	@Override
	public void initialize() {
		Main.addDetachableToolComposite("Kampf", "NSCs", 900, 800, () -> {
			controller = new NPCsController();
			return controller.getRoot();
		});
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see plugins.Plugin#load()
	 */
	@Override
	public void load() {}

}
