BaseSequencerObject {
	/**
	 * xFactor = 50
	 * default value for a 1-second long block, or equivalently:
	 * 1 beat at 60 bpm
	 * so ordinarily, let's say a soundfile block lasts 2 seconds, and zoom = 1@1 
	 * this block will render with a width of 100 pixels
	 **/
	classvar <xFactor = 50; 
	
	classvar <yFactor = 40;
	classvar <moveWidgetPixelsWidth = 5;
	// reference to state
	var id;

	// view variables
	var color, <bounds, initialBounds;
	var initialCursor;

	// state-action variables
	var action = nil;
	var <selected = false, toRefresh = true;
	var zoom;


	getAction { arg x, y;
		var innerArea = bounds.insetBy(moveWidgetPixelsWidth, 0);
		^case
			{innerArea.contains(x@y)} { 'move' }
			{x < innerArea.left} { 'resizeLeft' }
			{x > innerArea.right} { 'resizeRight' };
	}

	*new { arg event;
		^super.new.init(event)
	}

	showMenu { arg x, y;
		^Menu(
			MenuAction("copy (cmd-c)", 	{ Clipboard.add(id) }),
			MenuAction("paste (cmd-v)", {
				var absoluteTime = x / (xFactor * zoom.x);
				var absoluteExtension = y / (yFactor * zoom.y);
				Dispatcher((
					type: 'pasteObjects',
					payload: (
						x: absoluteTime,
						y: absoluteExtension,
						items: Clipboard.normalizedItems
					))
				);
				Clipboard.clear;
			}),
   		MenuAction("cut (cmd-x)", 	{ "cut item".postln; }),
   		MenuAction("slice (cmd-d)", { "slice".postln 	}),
		).front;
	}

	resetBounds { arg event;
		var rect = this.getRect(event);
		bounds.set(
			rect.left,
			rect.top,
			rect.width,
			rect.height
		);
	}

	getRect { arg event;
		var timestamp, length;

		length = event.resolveLength;
		timestamp = event.resolveTimestamp;


		^Rect(
			timestamp * xFactor * zoom.x,
			event.channel * yFactor * zoom.y,
			length * xFactor * zoom.x,
			yFactor * zoom.y
		);
	}

	select {
		selected = true;
		^this.bounds;
	}

	unselect {
		selected = false;
	}

	objectUpdated { arg payload;
		if (id == payload.id) {
			this.resetBounds(payload);
		}
	}

	init { arg event;
		id = event.id;
		color = Color.rand;
		initialCursor = 0@0;
		zoom = 1@1;
		bounds = this.getRect(event);
		initialBounds = bounds.copy;

		Dispatcher.addListener('objectUpdated', { arg payload;
			this.objectUpdated(payload)
		})
	}



	renderView { arg origin, parentBounds;
		var renderBounds = bounds.moveBy(origin.x, origin.y);
		if (renderBounds.intersects(parentBounds).not) { ^false };
		
		Pen.smoothing = true;
		Pen.addRect(renderBounds);
		Pen.color = color;
	  Pen.draw;
		
		if (selected) {
			Pen.addRect(renderBounds);
   		Pen.strokeColor = Color.black;
   		Pen.stroke;
		};
	}

	shouldMove {
		^selected
	}

	moveAction { arg x, y, snap;
		if (this.shouldMove) {
			if ((snap && ((bounds.origin.x % x.asInteger).magnitude != 0.0)), {
				var snappedValue = bounds.origin.x.roundUp(x);
				bounds = bounds.moveTo(snappedValue, bounds.origin.y);
				}, {
					bounds = bounds.moveBy(x, y)
				});
		};
		this.updateState;
	}

	moveBy { arg x, y;
		bounds = bounds.moveBy(x, y);
		this.updateState;
	}

	moveTo { arg x, y;
		bounds = bounds.moveTo(x, y);
		this.updateState;
	}

	// getTick {
	// 	^Store.at(id).timestamp;
	// }
	
	mouseDownAction { arg x, y, modifiers, buttonNumber, clickCount;
		color.alpha = 0.8;
		action = this.getAction(x, y);
		selected = true;
		initialBounds = bounds.copy;
		initialCursor = x@y;


		if (buttonNumber == 1) { this.showMenu(x, y) }
	}

	mouseMoveAction { arg x, y, modifiers, quantX;
		var difference, newOrigin, quantY;
		if (selected) {
			newOrigin = x@y - (initialCursor - initialBounds.origin);

			difference = bounds.origin - newOrigin;
			toRefresh = bounds.origin != newOrigin;
			
			quantY = yFactor * zoom.y;

			switch (action,
				'move', {
					quantX !? { newOrigin.x = newOrigin.x.round(quantX) };
					quantY !? { newOrigin.y = max(0, newOrigin.y.round(quantY)) };
					bounds.origin = newOrigin;
				},
				'resizeLeft', {
					bounds.set(
						bounds.left - difference.x,
						bounds.top,
						max(moveWidgetPixelsWidth, bounds.width + difference.x),
						bounds.height);
				},
				'resizeRight', {
					bounds.set(
						bounds.left,
						bounds.top,
						max(moveWidgetPixelsWidth, initialBounds.width - difference.x),
						bounds.height);
				}
			);
		}
	}

	mouseUpAction {
		color.alpha = 1;
		if (selected && bounds != initialBounds) {
			this.updateState;
		}
	}

	updateState {
		// call these variables 'absolute<Name>' because the store takes care of updating the events themselves with
		// bpm values
		var absoluteTime = bounds.origin.x / (xFactor * zoom.x);
		var absoluteExtension = bounds.origin.y / (yFactor * zoom.y);
		var absoluteLength = bounds.width / (xFactor * zoom.x);
		Dispatcher((
			type: 'moveObject',
			payload: (
				id: id,
				x: absoluteTime,
				y: absoluteExtension,
				length: absoluteLength
				)
		));
	}

	contains { arg x_y;
		^bounds.contains(x_y)
	}

	zoomBy { arg zoomX = 1, zoomY = 1;
		zoom.x = zoom.x * zoomX;
		zoom.y = zoom.y * zoomY;
		bounds.set(
			bounds.left * zoomX,
			bounds.top * zoomY,
			bounds.width * zoomX,
			bounds.height * zoomY
		);
	}
}

Cursor {

	classvar <xFactor = 50; 
	classvar <yFactor = 40;

	// view variables
	var color, <>bounds, initialBounds;

	var zoom;
	var <selected = false;

	*new { arg event;
		^super.new.init(event)
	}

	getRect { arg event;
		
		^Rect(
			event.x,
			event.y,
			1,
			yFactor
		);
	}

	init { arg event;
		color = SequencerTheme.darkGrey;
		zoom = 1@1;

		bounds = this.getRect(event);
	}

	renderView { arg origin;
		var renderBounds = bounds.moveBy(origin.x, origin.y);
		Pen.smoothing = true;
		Pen.color = color;
		
		if (bounds.width == 1, {
			Pen.line(renderBounds.origin, renderBounds.leftBottom);
	  	Pen.stroke;
	  	}, {
	  	Pen.addRect(renderBounds);
	  	Pen.draw;
	  });
	}

	setBounds { arg rect;
		bounds.set(
			rect.left,
			rect.top,
			rect.width,
			rect.height
		);
	}

	shouldMove {
		^true
	}

	moveAction { arg x, y, snap;
		if (this.shouldMove) {
			if ((snap && (bounds.origin.x % x != 0)), {
				var snappedValue = bounds.origin.x.roundUp(x);
				bounds = bounds.moveTo(snappedValue, bounds.origin.y);
				}, {
					bounds = bounds.moveBy(x, y)
			});
		}
	}

	mouseDownAction { arg x, y, modifiers, buttonNumber, clickCount;
		bounds.left = x;
		bounds.width = 1;
		bounds.top = y.trunc(yFactor * zoom.y);
	}

	moveTo { arg x, y;
		bounds.left = x;
		bounds.top = y;
	}

	moveBy { arg x, y;
		bounds = bounds.moveBy(x, y);
	}


	zoomBy { arg zoomX = 1, zoomY = 1;
		zoom.x = zoom.x * zoomX;
		zoom.y = zoom.y * zoomY;
		bounds.set(
			bounds.left * zoomX,
			bounds.top * zoomY,
			bounds.width * zoomX,
			bounds.height * zoomY
		);
	}
}

SequenceableBlock {
	/**
	 * xFactor = 50
	 * default value for a 1-second long block, or equivalently:
	 * 1 beat at 60 bpm
	 * so ordinarily, let's say a soundfile block lasts 2 seconds, and zoom = 1@1 
	 * this block will render with a width of 100 pixels
	 **/
	classvar <xFactor = 50; 
	
	classvar <yFactor = 40;
	classvar <moveWidgetPixelsWidth = 5;
	// reference to state
	var id;

	// view variables
	var color, <bounds, initialBounds;
	var initialCursor;

	// state-action variables
	var action = nil;
	var <selected = false, toRefresh = true;
	var zoom;


	getAction { arg x, y;
		var innerArea = bounds.insetBy(moveWidgetPixelsWidth, 0);
		^case
			{innerArea.contains(x@y)} {'move'}
			{x < innerArea.left} {'resizeLeft'}
			{x > innerArea.right} {'resizeRight'};
	}

	*new { arg event;
		^super.new.init(event)
	}

	showMenu { arg x, y;
		^Menu(
			MenuAction("copy (cmd-c)", 	{ Clipboard.add(id) }),
			MenuAction("paste (cmd-v)", {
				var absoluteTime = x / (xFactor * zoom.x);
				var absoluteExtension = y / (yFactor * zoom.y);
				Dispatcher((
					type: 'pasteObjects',
					payload: (
						x: absoluteTime,
						y: absoluteExtension,
						items: Clipboard.normalizedItems
					))
				);
				Clipboard.clear;
			}),
   		MenuAction("cut (cmd-x)", 	{ "cut item".postln; }),
   		MenuAction("slice (cmd-d)", { "slice".postln 	}),
		).front;
	}

	resetBounds { arg event;
		var rect = this.getRect(event);
		bounds.set(
			rect.left,
			rect.top,
			rect.width,
			rect.height
		);
	}

	getRect { arg event;
		var timestamp, length;

		length = event.resolveLength;
		timestamp = event.resolveTimestamp;


		^Rect(
			timestamp * xFactor * zoom.x,
			event.channel * yFactor * zoom.y,
			length * xFactor * zoom.x,
			yFactor * zoom.y
		);
	}

	select {
		selected = true;
		^this.bounds;
	}

	unselect {
		selected = false;
	}

	objectUpdated { arg payload;
		if (id == payload.id) {
			this.resetBounds(payload);
		}
	}

	init { arg event;
		id = event.id;
		color = Color.rand;
		initialCursor = 0@0;
		zoom = 1@1;
		bounds = this.getRect(event);
		initialBounds = bounds.copy;

		Dispatcher.addListener('objectUpdated', { arg payload;
			this.objectUpdated(payload)
		})
	}



	renderView { arg origin, parentBounds;
		var renderBounds = bounds.moveBy(origin.x, origin.y);
		if (renderBounds.intersects(parentBounds).not) { ^false };
		
		Pen.smoothing = true;
		Pen.addRect(renderBounds);
		Pen.color = color;
	  Pen.draw;
		
		if (selected) {
			Pen.addRect(renderBounds);
   		Pen.strokeColor = SequencerTheme.darkGrey;
   		Pen.stroke;
		};
	}

	shouldMove {
		^selected
	}

	moveAction { arg x, y, snap;
		if (this.shouldMove) {
			if ((snap && ((bounds.origin.x % x.asInteger).magnitude != 0.0)), {
				var snappedValue = bounds.origin.x.roundUp(x);
				bounds = bounds.moveTo(snappedValue, bounds.origin.y);
				}, {
					bounds = bounds.moveBy(x, y)
				});
		};
		this.updateState;
	}

	moveBy { arg x, y;
		bounds = bounds.moveBy(x, y);
		this.updateState;
	}

	moveTo { arg x, y;
		bounds = bounds.moveTo(x, y);
		this.updateState;
	}

	// getTick {
	// 	^Store.at(id).timestamp;
	// }
	
	mouseDownAction { arg x, y, modifiers, buttonNumber, clickCount;
		color.alpha = 0.8;
		action = this.getAction(x, y);
		selected = true;
		initialBounds = bounds.copy;
		initialCursor = x@y;


		if (buttonNumber == 1) { this.showMenu(x, y) }
	}

	mouseMoveAction { arg x, y, modifiers, quantX;
		var difference, newOrigin, quantY;
		if (selected) {
			newOrigin = x@y - (initialCursor - initialBounds.origin);

			difference = bounds.origin - newOrigin;
			toRefresh = bounds.origin != newOrigin;
			
			quantY = yFactor * zoom.y;

			switch (action,
				'move', {
					quantX !? { newOrigin.x = newOrigin.x.round(quantX) };
					quantY !? { newOrigin.y = max(0, newOrigin.y.round(quantY)) };
					bounds.origin = newOrigin;
				},
				'resizeLeft', {
					bounds.set(
						bounds.left - difference.x,
						bounds.top,
						max(moveWidgetPixelsWidth, bounds.width + difference.x),
						bounds.height);
				},
				'resizeRight', {
					bounds.set(
						bounds.left,
						bounds.top,
						max(moveWidgetPixelsWidth, initialBounds.width - difference.x),
						bounds.height);
				}
			);
		}
	}

	mouseUpAction {
		color.alpha = 1;
		if (selected && bounds != initialBounds) {
			this.updateState;
		}
	}

	updateState {
		// call these variables 'absolute<Name>' because the store takes care of updating the events themselves with
		// bpm values
		var absoluteTime = bounds.origin.x / (xFactor * zoom.x);
		var absoluteExtension = bounds.origin.y / (yFactor * zoom.y);
		var absoluteLength = bounds.width / (xFactor * zoom.x);
		Dispatcher((
			type: 'moveObject',
			payload: (
				id: id,
				x: absoluteTime,
				y: absoluteExtension,
				length: absoluteLength
				)
		));
	}

	contains { arg x_y;
		^bounds.contains(x_y)
	}

	zoomBy { arg zoomX = 1, zoomY = 1;
		zoom.x = zoom.x * zoomX;
		zoom.y = zoom.y * zoomY;
		bounds.set(
			bounds.left * zoomX,
			bounds.top * zoomY,
			bounds.width * zoomX,
			bounds.height * zoomY
		);
	}
}


SequenceableSoundfileBlock : SequenceableBlock {
	var soundfileMod, <waveformObject, <startPos;
	*new { arg event;
		^super.new(event).initSoundfile(event);
	}

	initSoundfile { arg event;
		soundfileMod = Mod(event.soundfile);
		startPos = event.startPos;
		this.getWaveform;
		^this;
	}

	renderView { arg origin, parentBounds;
		super.renderView(origin, parentBounds);
		this.renderWaveform(origin);
	}

	objectUpdated { arg payload;
		super.objectUpdated(payload);
		payload.startPos !? { |startPos|
			startPos = startPos;
		};
	}

	zoomBy { arg zoomX = 1, zoomY = 1;
		super.zoomBy(zoomX, zoomY);
		this.getWaveform;
	}

	getWaveform {
		waveformObject = soundfileMod.getWaveform(zoom.x);
	}

	renderWaveform { arg origin;

		var waveform = waveformObject.waveform;
		var renderBounds = bounds.moveBy(origin.x, origin.y);
		var height = renderBounds.height;
		var waveformColor = color.multiply(Color(0.5, 0.5, 0.5));

		Pen.smoothing = true;
		Pen.strokeColor = waveformColor;
		
		if (waveform.size > 0) {
			var middle = (renderBounds.leftTop + renderBounds.leftBottom) / 2;
			min(renderBounds.width, waveform.size).do { arg index;
				var data = waveform[index];
				var max = middle + Point(0, data[0] * height / 2);
				var min = middle + Point(0, data[1] * height / 2);

				Pen.line(max, min);
				Pen.fillStroke;
				middle.x = middle.x + 1;
			}
		}
	}
}